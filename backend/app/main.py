"""Samajh Sakhi API (FastAPI).

Run:  python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
Docs: http://127.0.0.1:8000/docs
"""
from __future__ import annotations

import io
import logging
import time
import wave
from collections import defaultdict
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Query, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, Response, StreamingResponse

from . import catalog, db, gnani, intent, translate
from .config import settings
from .schemas import (ConfirmIn, TranslateIn, ConfirmOut, HistoryIn, InterpretIn, LinkUpdate, PairIn, PhraseIn,
                      ProfileUpdate, RegisterIn, RegisterOut, ShareIn, SpeakIn)

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("samajh")

ALLOWED_EXT = {"wav", "mp3", "ogg", "flac", "aac", "m4a"}
MIME = {"wav": "audio/wav", "mp3": "audio/mpeg", "ogg": "audio/ogg", "flac": "audio/flac",
        "aac": "audio/aac", "m4a": "audio/mp4"}
MAX_AUDIO_SECONDS = 60  # Gnani Prisma REST limit


@asynccontextmanager
async def lifespan(_: FastAPI):
    if settings.require_access_code and not settings.registration_code:
        raise RuntimeError("REQUIRE_ACCESS_CODE is on but REGISTRATION_CODE is empty. Set an access code in backend/.env.")
    db.init_db()
    intent.load_model()
    if not settings.registration_code:
        log.warning("REGISTRATION_CODE is empty: anyone who finds this server can sign up and spend your Gnani credits.")
    if not settings.api_keys:
        log.warning("No Gnani API key configured. Set GNANI_API_KEY in backend/.env")
    yield


app = FastAPI(title="Vaani API", version="1.1.0", lifespan=lifespan)


def err(status: int, code: str, message: str) -> HTTPException:
    return HTTPException(status_code=status, detail={"code": code, "message": message})


@app.exception_handler(gnani.GnaniError)
async def gnani_error_handler(_: Request, exc: gnani.GnaniError):
    log.warning("Gnani error: %s", exc)
    return JSONResponse(status_code=exc.status, content={"detail": {"code": exc.code, "message": exc.message}})


@app.exception_handler(Exception)
async def unhandled(_: Request, exc: Exception):
    log.exception("Unhandled error")
    return JSONResponse(status_code=500, content={"detail": {"code": "server_error", "message": "Something went wrong on the server."}})


# ---------------------------------------------------------------- auth
def current_user(authorization: Optional[str] = Header(None)) -> dict:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise err(401, "unauthorized", "Please sign in again.")
    user = db.user_by_token(authorization[7:].strip())
    if not user:
        raise err(401, "unauthorized", "Please sign in again.")
    return user


_reg_log: dict = defaultdict(list)
_pair_fail: dict = defaultdict(list)


def _rate_ok(store: dict, key: str, limit: int, window: int) -> bool:
    now = time.time()
    store[key] = [t for t in store[key] if now - t < window]
    return len(store[key]) < limit


def _check_lang(code: str, allowed) -> str:
    if code not in allowed:
        raise err(400, "unsupported_language", f"Language '{code}' is not supported.")
    return code


# ---------------------------------------------------------------- health
@app.get("/health")
def health():
    return {
        "status": "ok", "service": "samajh-sakhi", "gnani_configured": bool(settings.api_keys),
        "access_code_required": bool(settings.registration_code),
        "stt_model": settings.stt_model, "tts_model": settings.tts_model,
    }


# ---------------------------------------------------------------- users
@app.post("/api/users/register", response_model=RegisterOut)
def register(body: RegisterIn, request: Request, x_registration_code: Optional[str] = Header(None)):
    if settings.registration_code and x_registration_code != settings.registration_code:
        raise err(403, "bad_access_code", "The access code is not correct.")
    ip = request.client.host if request.client else "?"
    if not _rate_ok(_reg_log, ip, 20, 3600):
        raise err(429, "too_many_requests", "Too many sign-ups. Please try again later.")
    _reg_log[ip].append(time.time())
    lang = body.preferred_language if body.preferred_language in catalog.INPUT_LANGUAGES else "mr-IN"
    user_id, token = db.create_user(body.display_name.strip(), lang)
    return RegisterOut(user_id=user_id, token=token)


def _profile(u: dict) -> dict:
    return {
        "user_id": u["id"], "display_name": u["display_name"], "preferred_language": u["preferred_language"],
        "emergency_name": u["emergency_name"], "emergency_phone": u["emergency_phone"],
        "save_history": bool(u["save_history"]),
    }


@app.get("/api/users/me")
def me(user: dict = Depends(current_user)):
    return _profile(user)


@app.put("/api/users/me")
def update_me(body: ProfileUpdate, user: dict = Depends(current_user)):
    fields = body.model_dump(exclude_none=True)
    if "save_history" in fields:
        fields["save_history"] = 1 if fields["save_history"] else 0
    db.update_user(user["id"], fields)
    return _profile(db.get_user(user["id"]))


@app.delete("/api/users/me")
def delete_me(user: dict = Depends(current_user)):
    db.delete_user(user["id"])
    return {"deleted": True}


# ---------------------------------------------------------------- catalog
@app.get("/api/intents")
def intents(user: dict = Depends(current_user)):
    return {
        "categories": catalog.CATEGORIES,
        "intents": [
            {"intent": k, "category": v["category"], "meaning": v["meaning"], "message": v["message"]}
            for k, v in catalog.INTENTS.items()
        ],
    }


# ---------------------------------------------------------------- speech
def _limit(user: dict, kind: str, cap: int, global_cap: int = 0) -> None:
    if db.usage_count(user["id"], kind) >= cap:
        raise err(429, "daily_limit", "Daily limit reached. Please try again tomorrow or use the quick cards.")
    if global_cap and db.usage_total(kind) >= global_cap:
        raise err(429, "service_limit", "The service has reached its daily limit. Please use the quick cards or try tomorrow.")
    db.bump_usage(user["id"], kind)


def build_keyterms(user: dict, phrases: list) -> list:
    """Personal hints for Prisma v3.0: saved phrases (and alternatives), the user's name, emergency contact."""
    terms = [user.get("display_name") or "", user.get("emergency_name") or ""]
    for p in phrases:  # list_phrases is ordered by most used first
        terms.append(p["alias"])
        terms.extend(p.get("alternatives", []))
    return gnani.clean_keyterms(terms, settings.keyterms_max)


def _wav_seconds(data: bytes) -> Optional[float]:
    try:
        with wave.open(io.BytesIO(data)) as w:
            return w.getnframes() / float(w.getframerate() or 1)
    except (wave.Error, EOFError):
        return None


@app.post("/api/speech/transcribe")
async def transcribe(
    audio: UploadFile = File(...),
    language_code: str = Form("mr-IN"),
    output_language: str = Form(""),
    use_keyterms: bool = Form(True),
    user: dict = Depends(current_user),
):
    _check_lang(language_code, catalog.INPUT_LANGUAGES)
    name = audio.filename or "recording.wav"
    ext = name.rsplit(".", 1)[-1].lower() if "." in name else "wav"
    if ext not in ALLOWED_EXT:
        raise err(415, "unsupported_audio", "This audio format is not supported. Please record again.")
    data = await audio.read(settings.max_upload_bytes + 1)
    if len(data) > settings.max_upload_bytes:
        raise err(413, "upload_too_large", f"The recording is larger than {settings.max_upload_mb} MB.")
    if len(data) < 1000:
        raise err(400, "empty_audio", "The recording is empty. Please try again.")
    if ext == "wav":
        secs = _wav_seconds(data)
        if secs is None:
            raise err(400, "bad_audio", "The recording could not be read. Please record again.")
        if secs < 0.3:
            raise err(400, "empty_audio", "The recording was too short. Please hold the button and speak.")
        if secs > MAX_AUDIO_SECONDS + 1:
            raise err(400, "audio_too_long", f"Please keep recordings under {MAX_AUDIO_SECONDS} seconds.")

    _limit(user, "stt", settings.daily_stt_limit, settings.global_daily_stt_limit)
    stt_lang = catalog.INPUT_LANGUAGES[language_code]
    phrases = db.list_phrases(user["id"])
    keyterms = build_keyterms(user, phrases) if use_keyterms else []
    heard = await run_in_threadpool(gnani.transcribe, data, name, MIME.get(ext, "audio/wav"), stt_lang, keyterms)
    transcript, detected = heard.text, heard.detected_language

    lang, out_lang, note = catalog.resolve_languages(language_code, output_language, detected)
    result = intent.interpret(transcript, lang, out_lang, phrases)
    if note and not result.get("warning"):
        result["warning"] = note
    return {
        "transcript": transcript,
        "language_code": lang,
        "requested_language": language_code,
        "detected_language_code": detected,
        "keyterms_sent": len(keyterms) if settings.stt_is_v3 and settings.keyterms_enabled else 0,
        "interpretation": result,
        # kept for compatibility with the earlier API
        "suggested_intent": result["intent"],
        "suggested_meaning": result["meaning"],
    }


@app.post("/api/translate")
async def translate_text(body: TranslateIn, user: dict = Depends(current_user)):
    """Convert a message into Marathi, Hindi or English (see app/translate.py for how)."""
    _check_lang(body.target_language, {"mr-IN", "hi-IN", "en-IN"})
    if not body.text.strip():
        raise err(400, "empty_text", "There is nothing to translate.")
    _limit(user, "translate", settings.daily_translate_limit)
    return await run_in_threadpool(translate.translate, body.text, body.target_language, db.list_phrases(user["id"]), body.intent)


@app.post("/api/speech/interpret")
def interpret_text(body: InterpretIn, user: dict = Depends(current_user)):
    _check_lang(body.language, catalog.INPUT_LANGUAGES)
    lang, out_lang, _ = catalog.resolve_languages(body.language, body.output_language or "")
    return intent.interpret(body.text, lang, out_lang, db.list_phrases(user["id"]))


def _check_speak(body: SpeakIn) -> str:
    _check_lang(body.language, catalog.OUTPUT_LANGUAGES)
    if body.style not in gnani.STYLES:
        raise err(400, "unsupported_style", "That speaking style is not available.")
    text = body.text.strip()
    if not gnani.strip_tags(text):
        raise err(400, "empty_text", "There is nothing to speak.")
    return text


@app.post("/api/speech/speak")
async def speak(body: SpeakIn, user: dict = Depends(current_user)):
    text = _check_speak(body)
    _limit(user, "tts", settings.daily_tts_limit, settings.global_daily_tts_limit)
    audio, ctype = await run_in_threadpool(gnani.synthesize, text, body.language, body.speed, body.voice, body.style)
    return Response(content=audio, media_type=ctype, headers={"Cache-Control": "no-store"})


@app.post("/api/speech/speak/stream")
async def speak_stream(body: SpeakIn, user: dict = Depends(current_user)):
    """Streaming Timbre (SSE): the WAV starts arriving while it is still being generated."""
    text = _check_speak(body)
    _limit(user, "tts", settings.daily_tts_limit, settings.global_daily_tts_limit)
    gen = gnani.synthesize_stream(text, body.language, body.speed, body.voice, body.style)
    first = await run_in_threadpool(next, gen)  # raises GnaniError before any bytes are sent

    def body_iter():
        yield first
        yield from gen

    return StreamingResponse(body_iter(), media_type="audio/wav", headers={"Cache-Control": "no-store"})


@app.get("/api/speech/voices")
def voices(user: dict = Depends(current_user)):
    return {
        "default": settings.tts_voice, "voices": settings.tts_voices, "model": settings.tts_model,
        "supports_styles": settings.tts_is_v3, "styles": list(gnani.STYLES),
        "speed_min": 0.85, "speed_max": 1.15,
    }


# ---------------------------------------------------------------- confirmation
def _record_history(user: dict, **kw) -> Optional[int]:
    if not user["save_history"]:
        return None
    return db.add_history(user["id"], **kw)


@app.post("/api/interpret/confirm", response_model=ConfirmOut)
def confirm(body: ConfirmIn, user: dict = Depends(current_user)):
    uid = user["id"]
    saved = False
    transcript = body.transcript.strip()
    if body.save_as_phrase and transcript:
        existing = db.find_phrase_by_alias(uid, transcript)
        category = catalog.INTENTS.get(body.intent, {}).get("category", "custom")
        if existing:
            db.update_phrase(uid, existing["id"], existing["alias"], existing["alternatives"],
                             body.canonical_meaning, body.intent, body.language, category)
        else:
            db.add_phrase(uid, transcript, [], body.canonical_meaning, body.intent, body.language, category)
        saved = True
    if body.matched_phrase_id:
        db.bump_phrase_usage(uid, body.matched_phrase_id)
    hid = _record_history(
        user, transcript=transcript, source_language=body.language, meaning=body.canonical_meaning,
        intent=body.intent, confirmed=True, final_text=(body.final_text or body.canonical_meaning),
        output_language=body.output_language or body.language,
    )
    return ConfirmOut(confirmed=True, phrase_saved=saved, history_id=hid)


# ---------------------------------------------------------------- phrases
def _phrase_out(p: dict) -> dict:
    return {k: p[k] for k in ("id", "alias", "alternatives", "canonical_meaning", "intent", "language",
                              "category", "usage_count", "created_at")}


@app.get("/api/phrases")
def phrases_list(q: str = "", category: str = "", language: str = "", user: dict = Depends(current_user)):
    return [_phrase_out(p) for p in db.list_phrases(user["id"], q, category, language)]


@app.post("/api/phrases", status_code=201)
def phrases_add(body: PhraseIn, user: dict = Depends(current_user)):
    if db.find_phrase_by_alias(user["id"], body.alias):
        raise err(409, "duplicate_phrase", "You already saved this phrase.")
    category = body.category or catalog.INTENTS.get(body.intent, {}).get("category", "custom")
    p = db.add_phrase(user["id"], body.alias, body.alternatives, body.canonical_meaning, body.intent, body.language, category)
    return _phrase_out(p)


@app.put("/api/phrases/{phrase_id}")
def phrases_update(phrase_id: int, body: PhraseIn, user: dict = Depends(current_user)):
    category = body.category or catalog.INTENTS.get(body.intent, {}).get("category", "custom")
    p = db.update_phrase(user["id"], phrase_id, body.alias, body.alternatives, body.canonical_meaning, body.intent, body.language, category)
    if not p:
        raise err(404, "not_found", "Phrase not found.")
    return _phrase_out(p)


@app.delete("/api/phrases/{phrase_id}")
def phrases_delete(phrase_id: int, user: dict = Depends(current_user)):
    if not db.delete_phrase(user["id"], phrase_id):
        raise err(404, "not_found", "Phrase not found.")
    return {"deleted": True}


# ---------------------------------------------------------------- history
def _hist_out(h: dict) -> dict:
    return {"id": h["id"], "transcript": h["transcript"], "source_language": h["source_language"],
            "meaning": h["meaning"], "intent": h["intent"], "confirmed": bool(h["confirmed"]),
            "final_text": h["final_text"], "output_language": h["output_language"],
            "shared": bool(h["shared"]), "created_at": h["created_at"]}


@app.get("/api/history")
def history_list(q: str = "", limit: int = Query(100, ge=1, le=500), user: dict = Depends(current_user)):
    return [_hist_out(h) for h in db.list_history(user["id"], q, limit)]


@app.post("/api/history", status_code=201)
def history_add(body: HistoryIn, user: dict = Depends(current_user)):
    hid = _record_history(user, transcript=body.transcript, source_language=body.language, meaning=body.meaning,
                          intent=body.intent, confirmed=body.confirmed, final_text=body.final_text,
                          output_language=body.output_language)
    return {"id": hid, "saved": hid is not None}


@app.delete("/api/history")
def history_clear(user: dict = Depends(current_user)):
    return {"deleted": db.clear_history(user["id"])}


@app.get("/api/history/{history_id}")
def history_get(history_id: int, user: dict = Depends(current_user)):
    h = db.get_history(user["id"], history_id)
    if not h:
        raise err(404, "not_found", "Conversation not found.")
    return _hist_out(h)


@app.delete("/api/history/{history_id}")
def history_delete(history_id: int, user: dict = Depends(current_user)):
    if not db.delete_history(user["id"], history_id):
        raise err(404, "not_found", "Conversation not found.")
    return {"deleted": True}


@app.post("/api/history/{history_id}/share")
def history_share(history_id: int, body: ShareIn, user: dict = Depends(current_user)):
    if not db.set_history_shared(user["id"], history_id, body.shared):
        raise err(404, "not_found", "Conversation not found.")
    return {"shared": body.shared}


# ---------------------------------------------------------------- caregiver (consent based)
@app.post("/api/caregiver/code")
def caregiver_code(user: dict = Depends(current_user)):
    code, expires = db.create_pair_code(user["id"])
    return {"code": code, "expires_at": expires}


@app.post("/api/caregiver/pair")
def caregiver_pair(body: PairIn, user: dict = Depends(current_user)):
    if not _rate_ok(_pair_fail, user["id"], 10, 3600):
        raise err(429, "too_many_requests", "Too many wrong codes. Please try again later.")
    link = db.redeem_pair_code(body.code, user["id"])
    if not link:
        _pair_fail[user["id"]].append(time.time())
        raise err(400, "bad_code", "That code is wrong or has expired.")
    person = db.get_user(link["user_id"])
    return {"link_id": link["id"], "user_id": link["user_id"], "person_name": person["display_name"] or "Person",
            "note": "Nothing is shared until the person gives permission."}


def _link_out(l: dict) -> dict:
    return {"id": l["id"], "caregiver_name": l.get("caregiver_name", ""), "share_phrases": bool(l["share_phrases"]),
            "share_history": bool(l["share_history"]), "share_emergency": bool(l["share_emergency"]),
            "created_at": l["created_at"]}


@app.get("/api/caregiver/links")
def caregiver_links(user: dict = Depends(current_user)):
    return [_link_out(l) for l in db.links_for_user(user["id"])]


@app.put("/api/caregiver/links/{link_id}")
def caregiver_link_update(link_id: int, body: LinkUpdate, user: dict = Depends(current_user)):
    if not db.update_link(user["id"], link_id, body.share_phrases, body.share_history, body.share_emergency):
        raise err(404, "not_found", "Link not found.")
    return {"updated": True}


@app.delete("/api/caregiver/links/{link_id}")
def caregiver_link_delete(link_id: int, user: dict = Depends(current_user)):
    if not db.delete_link(user["id"], link_id):
        raise err(404, "not_found", "Link not found.")
    return {"deleted": True}


@app.get("/api/caregiver/people")
def caregiver_people(user: dict = Depends(current_user)):
    return [
        {"link_id": l["id"], "user_id": l["user_id"], "name": l["person_name"] or "Person",
         "share_phrases": bool(l["share_phrases"]), "share_history": bool(l["share_history"]),
         "share_emergency": bool(l["share_emergency"])}
        for l in db.people_for_caregiver(user["id"])
    ]


def _require_link(user: dict, person_id: str) -> dict:
    link = db.link_for_caregiver(user["id"], person_id)
    if not link:
        raise err(403, "not_allowed", "You do not have access to this person.")
    return link


@app.get("/api/caregiver/people/{person_id}/history")
def caregiver_history(person_id: str, user: dict = Depends(current_user)):
    link = _require_link(user, person_id)
    items = db.shared_history(person_id, bool(link["share_history"]))
    return [{"id": h["id"], "final_text": h["final_text"], "meaning": h["meaning"],
             "output_language": h["output_language"], "created_at": h["created_at"]} for h in items]


@app.get("/api/caregiver/people/{person_id}/phrases")
def caregiver_phrases(person_id: str, user: dict = Depends(current_user)):
    link = _require_link(user, person_id)
    if not link["share_phrases"]:
        raise err(403, "not_shared", "The person has not shared their phrases.")
    return [_phrase_out(p) for p in db.list_phrases(person_id)]


@app.get("/api/caregiver/people/{person_id}/emergency")
def caregiver_emergency(person_id: str, user: dict = Depends(current_user)):
    link = _require_link(user, person_id)
    if not link["share_emergency"]:
        raise err(403, "not_shared", "The person has not shared emergency contact details.")
    p = db.get_user(person_id)
    return {"name": p["emergency_name"], "phone": p["emergency_phone"]}


@app.delete("/api/caregiver/people/{person_id}")
def caregiver_leave(person_id: str, user: dict = Depends(current_user)):
    if not db.leave_link(user["id"], person_id):
        raise err(404, "not_found", "Link not found.")
    return {"deleted": True}
