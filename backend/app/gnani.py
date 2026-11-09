"""Gnani AI client (Prisma speech-to-text, Timbre text-to-speech).

All Gnani calls happen here, on the backend. API keys never reach the app.
Request formats follow the official docs:
  STT  https://docs.gnani.ai/api/STT/speech-to-text   (Prisma v3.0: model=prisma-v3.0)
  TTS  https://docs.gnani.ai/api/TTS/tts-inference    (Timbre v3.0: model=timbre-v3.0)
  SSE  https://docs.gnani.ai/api/TTS/tts-sse          (streaming Timbre)
Evon (reasoning/LLM) is intentionally NOT integrated: no verified API contract
or account access was available, so interpretation uses the local ML + rules.
"""
from __future__ import annotations

import base64
import hashlib
import itertools
import json
import logging
import re
import threading
from collections import OrderedDict
from typing import Iterator, NamedTuple, Optional

import requests

from .config import settings
from .wavfix import describe, normalize_wav, streaming_wav_header

log = logging.getLogger("samajh.gnani")

_key_cycle = itertools.count()
_gate = threading.BoundedSemaphore(settings.gnani_max_concurrency)

_FRIENDLY = {
    "no_keys": "The speech service is not configured on the server.",
    "api_key_invalid": "The speech service rejected the server's credentials.",
    "credits_exhausted": "The speech service has no credits left.",
    "forbidden": "The speech service is not allowed for this account.",
    "rate_limited": "The speech service is busy. Please try again in a moment.",
    "timeout": "The speech service took too long. Please try again.",
    "service_unavailable": "The speech service is temporarily unavailable.",
    "bad_request": "The speech service could not process this request.",
    "empty_transcript": "We could not hear any speech. Please try again closer to the microphone.",
    "bad_response": "The speech service returned an unexpected answer.",
    "busy": "Another request is still running. Please try again.",
}


class GnaniError(Exception):
    def __init__(self, code: str, status: int = 502, detail: str = ""):
        self.code = code
        self.status = status
        self.message = _FRIENDLY.get(code, "The speech service had a problem.")
        self.detail = detail
        super().__init__(f"{code}: {detail}" if detail else code)


def _ordered_keys() -> list:
    keys = settings.api_keys
    if not keys:
        raise GnaniError("no_keys", 503)
    start = next(_key_cycle) % len(keys)
    return keys[start:] + keys[:start]


def _map_http_error(resp: requests.Response) -> GnaniError:
    body = ""
    try:
        body = resp.text[:300]
    except Exception:
        pass
    s = resp.status_code
    if s == 401:
        return GnaniError("api_key_invalid", 502, body)
    if s == 402:
        return GnaniError("credits_exhausted", 503, body)
    if s == 403:
        return GnaniError("forbidden", 502, body)
    if s == 429:
        return GnaniError("rate_limited", 429, body)
    if s in (500, 502, 503, 504):
        return GnaniError("service_unavailable", 503, body)
    return GnaniError("bad_request", 400 if s == 400 else 502, body)


def _post_with_failover(build_request) -> requests.Response:
    """Try each key in rotation; fail over on auth/credit/rate-limit/5xx errors."""
    if not _gate.acquire(timeout=settings.gnani_timeout):
        raise GnaniError("busy", 429)
    try:
        last: Optional[GnaniError] = None
        for key in _ordered_keys():
            try:
                resp = build_request(key)
            except requests.Timeout:
                last = GnaniError("timeout", 504)
                continue
            except requests.RequestException as exc:
                last = GnaniError("service_unavailable", 503, str(exc))
                continue
            if resp.status_code == 200:
                return resp
            last = _map_http_error(resp)
            log.warning("Gnani HTTP %s (%s)", resp.status_code, last.code)
            if last.code == "bad_request":  # same request will fail with any key
                break
        raise last or GnaniError("service_unavailable", 503)
    finally:
        _gate.release()


def _extract_transcript(payload) -> str:
    if isinstance(payload, dict):
        for k in ("transcript", "text", "transcription"):
            v = payload.get(k)
            if isinstance(v, str):
                return v.strip()
        for k in ("data", "result", "results"):
            inner = payload.get(k)
            if isinstance(inner, list) and inner:
                inner = inner[0]
            if isinstance(inner, dict):
                got = _extract_transcript(inner)
                if got:
                    return got
    return ""


class Transcript(NamedTuple):
    text: str
    detected_language: str = ""  # Prisma v3.0 'detected_language_code' (e.g. 'mr-IN'); '' if absent


# Prisma accepts: indic-auto, or a comma separated list of 1-4 language codes.
STT_CODES = {"bn-IN", "en-IN", "gu-IN", "hi-IN", "kn-IN", "ml-IN", "mr-IN", "pa-IN", "ta-IN", "te-IN"}
_BAD_CONTEXT = ("<|", "|>")


def normalize_stt_language(value: str) -> str:
    """Validate a language_code for Prisma v3.0 ('indic-auto' or up to 4 comma separated codes)."""
    value = (value or "").strip()
    if value == "indic-auto":
        return value
    codes = [c.strip() for c in value.split(",") if c.strip()]
    if not 1 <= len(codes) <= 4 or any(c not in STT_CODES for c in codes):
        raise GnaniError("bad_request", 400, f"invalid language_code {value!r}")
    return ",".join(codes)


def clean_keyterms(terms, limit: int = 200) -> list:
    """Dedupe/trim hint words for Prisma `keyterms` (max 200; '<|' and '|>' are rejected by Gnani)."""
    out, seen = [], set()
    for t in terms or []:
        t = " ".join(str(t or "").split())
        for bad in _BAD_CONTEXT:
            t = t.replace(bad, "")
        if not t or len(t) > 80 or t.lower() in seen:
            continue
        seen.add(t.lower())
        out.append(t)
        if len(out) >= limit:
            break
    return out


def transcribe(audio: bytes, filename: str, content_type: str, stt_language: str,
               keyterms: Optional[list] = None) -> Transcript:
    """Prisma STT.

    stt_language: 'mr-IN', a comma list like 'hi-IN,en-IN' (max 4), or 'indic-auto'.
    keyterms: optional personal words/names to favour (v3.0 only, max 200). Gnani warns that
    hints can occasionally lower accuracy, so callers may switch them off (see scripts/eval_keyterms.py).
    """
    stt_language = normalize_stt_language(stt_language)
    terms = clean_keyterms(keyterms, settings.keyterms_max) if (settings.stt_is_v3 and settings.keyterms_enabled) else []

    def build(key: str):
        headers = {settings.stt_key_header: settings.stt_key_prefix + key}
        data = {settings.stt_language_field: stt_language}
        if settings.stt_format_field and settings.stt_format_value:
            data[settings.stt_format_field] = settings.stt_format_value
        data.update({k: str(v) for k, v in settings.stt_extra_fields.items()})
        if terms:
            data["keyterms"] = json.dumps(terms, ensure_ascii=False)
        files = {settings.stt_audio_field: (filename, audio, content_type)}
        return requests.post(settings.stt_url, headers=headers, data=data, files=files,
                             timeout=(10, settings.gnani_timeout))

    resp = _post_with_failover(build)
    try:
        payload = resp.json()
    except ValueError:
        raise GnaniError("bad_response", 502, resp.text[:200])
    if isinstance(payload, dict) and payload.get("success") is False:
        raise GnaniError("bad_request", 502, str(payload)[:200])
    text = _extract_transcript(payload)
    if not text:
        raise GnaniError("empty_transcript", 422)
    detected = payload.get("detected_language_code", "") if isinstance(payload, dict) else ""
    return Transcript(text, detected if isinstance(detected, str) else "")


# Small in-memory cache so replaying the same message does not spend credits.
# Memory only (never written to disk) because messages can be personal.
_cache: "OrderedDict[str, tuple]" = OrderedDict()
_CACHE_MAX = 64


# ---- expressive speech (Timbre v3.0 audio tags) ----
STYLES = ("normal", "urgent", "comfort", "slow_clear")
_TAG_RE = re.compile(r"<[^<>]{1,30}>")


def strip_tags(text: str) -> str:
    """Remove angle-bracket tags typed by a user, so only our own tags reach Timbre."""
    return " ".join(_TAG_RE.sub(" ", text or "").split())


def apply_style(text: str, style: str, speed: Optional[float] = None) -> tuple:
    """Return (text_with_audio_tags, speed). Tags only work on timbre-v3.0; v2.5 gets plain text.

    urgent     -> <shouting>  emergency messages
    comfort    -> <whispers>  soothing messages
    slow_clear -> <slow> + <pauses> between sentences, speed 0.85
    """
    text = strip_tags(text)
    if style not in STYLES or style == "normal" or not settings.tts_is_v3:
        return text, speed
    if style == "urgent":
        return f"<shouting> {text}", speed
    if style == "comfort":
        return f"<whispers> {text}", speed
    parts = [p.strip() for p in re.split(r"(?<=[.!?।])\s+", text) if p.strip()]
    return "<slow> " + " <pauses> ".join(parts or [text]), 0.85


def resolve_voice(voice: Optional[str]) -> str:
    """Voices are model specific. An unknown/old voice (e.g. a v2.5 name) falls back to the default."""
    if voice and voice in settings.tts_voices:
        return voice
    if voice:
        log.info("Voice %r is not available for %s; using %s", voice, settings.tts_model, settings.tts_voice)
    return settings.tts_voice


def synthesize(text: str, language: str, speed: Optional[float] = None, voice: Optional[str] = None,
               style: str = "normal") -> tuple:
    """Timbre TTS. Returns (audio_bytes, content_type)."""
    speed = settings.tts_speed if speed is None else speed
    text, speed = apply_style(text, style, speed)
    speed = max(0.85, min(1.15, float(speed)))  # documented range
    voice = resolve_voice(voice)
    ck = hashlib.sha256(f"{text}|{language}|{voice}|{speed}|{settings.tts_model}".encode()).hexdigest()
    if ck in _cache:
        _cache.move_to_end(ck)
        return _cache[ck]

    payload = {
        "text": text,
        "voice": voice,
        "model": settings.tts_model,
        "language": language,
        "speed": speed,
        "audio_config": settings.tts_audio_config,
    }

    def build(key: str):
        headers = {settings.tts_key_header: key, "Content-Type": "application/json"}
        return requests.post(settings.tts_url, headers=headers, json=payload, timeout=(10, settings.gnani_timeout))

    resp = _post_with_failover(build)
    ctype = resp.headers.get("Content-Type", "audio/wav").split(";")[0].strip()
    audio = resp.content
    if "json" in ctype or "text" in ctype or len(audio) < 100:
        raise GnaniError("bad_response", 502, audio[:200].decode("utf-8", "ignore"))
    log.info("Timbre returned %s, %d bytes, %s", ctype, len(audio), describe(audio))
    audio = normalize_wav(audio)  # fixes streamed/odd headers that Android cannot play
    result = (audio, "audio/wav" if audio[:4] == b"RIFF" else (ctype or "audio/wav"))
    _cache[ck] = result
    while len(_cache) > _CACHE_MAX:
        _cache.popitem(last=False)
    return result


# ---- streaming TTS (SSE) ----
STREAM_RATE = 24000


def synthesize_stream(text: str, language: str, speed: Optional[float] = None, voice: Optional[str] = None,
                      style: str = "normal") -> Iterator[bytes]:
    """Timbre SSE: yields a WAV header followed by PCM chunks as Gnani generates them.

    Gnani's SSE returns base64 audio inside `chunk` events and ends with `complete`.
    We request raw 16-bit PCM and add our own streaming WAV header, because the docs do not
    say how a WAV header is split across chunks. Only one Gnani request may be active at a
    time, so the concurrency gate is held until the stream ends.
    """
    speed = settings.tts_speed if speed is None else speed
    text, speed = apply_style(text, style, speed)
    speed = max(0.85, min(1.15, float(speed)))
    payload = {
        "text": text, "voice": resolve_voice(voice), "model": settings.tts_model, "language": language,
        "speed": speed,
        "audio_config": {"sample_rate": STREAM_RATE, "num_channels": 1, "sample_width": 2,
                         "encoding": "linear_pcm", "container": "raw"},
    }
    if not _gate.acquire(timeout=settings.gnani_timeout):
        raise GnaniError("busy", 429)
    try:
        resp = None
        last: Optional[GnaniError] = None
        for key in _ordered_keys():
            try:
                r = requests.post(settings.tts_sse_url, json=payload, stream=True,
                                  headers={settings.tts_key_header: key, "Content-Type": "application/json"},
                                  timeout=(10, settings.gnani_timeout))
            except requests.Timeout:
                last = GnaniError("timeout", 504)
                continue
            except requests.RequestException as exc:
                last = GnaniError("service_unavailable", 503, str(exc))
                continue
            if r.status_code == 200:
                resp = r
                break
            last = _map_http_error(r)
            if last.code == "bad_request":
                break
        if resp is None:
            raise last or GnaniError("service_unavailable", 503)
        # The WAV header is only sent together with the first audio chunk, so the caller can
        # still return a clean JSON error if Gnani fails before any audio exists.
        event, got_audio = "", False
        for raw in resp.iter_lines(decode_unicode=True):
            if raw is None:
                continue
            line = raw.strip()
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                try:
                    data = json.loads(line[5:].strip() or "{}")
                except ValueError:
                    continue
                if event == "error":
                    log.warning("Timbre SSE error: %s", data)
                    if not got_audio:
                        raise GnaniError("bad_response", 502, str(data)[:200])
                    break
                if data.get("audio"):
                    chunk = base64.b64decode(data["audio"])
                    if not got_audio:
                        chunk = streaming_wav_header(STREAM_RATE, 1, 2) + chunk
                    got_audio = True
                    yield chunk
                if event == "complete" or data.get("is_final"):
                    break
        if not got_audio:
            raise GnaniError("bad_response", 502, "stream ended without audio")
    finally:
        _gate.release()
