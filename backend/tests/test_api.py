from conftest import make_wav, new_user


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok"


def test_auth_required(client):
    assert client.get("/api/phrases").status_code == 401
    assert client.get("/api/phrases", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_interpret_water_and_negation(client):
    _, h = new_user(client)
    r = client.post("/api/speech/interpret", json={"text": "Hello I need water please", "language": "en-IN"}, headers=h).json()
    assert r["intent"] == "request_water" and not r["negated"] and r["needs_confirmation"]
    n = client.post("/api/speech/interpret", json={"text": "I don't need water", "language": "en-IN"}, headers=h).json()
    assert n["negated"] and "not" in n["suggested_message"].lower()
    d = client.post("/api/speech/interpret", json={"text": "I already drank water", "language": "en-IN"}, headers=h).json()
    assert d["already_done"] and "already" in d["suggested_message"].lower()
    p = client.post("/api/speech/interpret", json={"text": "please bring water", "language": "en-IN"}, headers=h).json()
    assert p["intent"] == "request_water" and not p["negated"]


def test_cross_language_output(client):
    _, h = new_user(client)
    r = client.post("/api/speech/interpret", json={"text": "mala pani pahije", "language": "mr-IN", "output_language": "hi-IN"}, headers=h).json()
    assert r["intent"] == "request_water" and "पानी" in r["suggested_message"]


def test_low_confidence(client):
    _, h = new_user(client)
    r = client.post("/api/speech/interpret", json={"text": "asdf qwer zxcv", "language": "en-IN"}, headers=h).json()
    assert r["confidence_level"] != "high" or r["intent"] == "other_unclear"
    assert r["warning"]


def test_transcribe_flow(client, fake_gnani):
    _, h = new_user(client)
    r = client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                    files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")})
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["transcript"] == "Hello I need water please"
    assert j["interpretation"]["intent"] == "request_water"
    assert j["suggested_intent"] == "request_water"
    url, kw = fake_gnani["calls"][-1]
    assert kw["headers"]["X-API-Key-ID"] == "test-key-not-real"
    assert "audio_file" in kw["files"] and kw["data"]["language_code"] == "en-IN"
    assert kw["data"]["model"] == "prisma-v3.0"
    assert "itn_native_numerals" not in kw["data"]  # no effect on Prisma v3.0


def test_hinglish_sends_both_codes(client, fake_gnani):
    _, h = new_user(client)
    client.post("/api/speech/transcribe", headers=h, data={"language_code": "hi-en"},
                files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")})
    assert fake_gnani["calls"][-1][1]["data"]["language_code"] == "hi-IN,en-IN"


def test_transcribe_validation(client, fake_gnani):
    _, h = new_user(client)
    big = b"RIFF" + b"0" * (2 * 1024 * 1024)
    assert client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                       files={"audio": ("a.wav", big, "audio/wav")}).status_code == 413
    assert client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                       files={"audio": ("a.wav", b"x" * 10, "audio/wav")}).status_code == 400
    assert client.post("/api/speech/transcribe", headers=h, data={"language_code": "xx-XX"},
                       files={"audio": ("a.wav", make_wav(), "audio/wav")}).status_code == 400
    assert client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                       files={"audio": ("a.exe", make_wav(), "audio/wav")}).status_code == 415
    assert fake_gnani["calls"] == []  # nothing invalid reached Gnani


def test_gnani_errors_are_friendly(client, fake_gnani):
    _, h = new_user(client)
    for status, code in [(401, "api_key_invalid"), (429, "rate_limited"), (503, "service_unavailable")]:
        fake_gnani["status"] = status
        r = client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                        files={"audio": ("a.wav", make_wav(), "audio/wav")})
        assert r.json()["detail"]["code"] == code
        assert "Traceback" not in r.text


def test_empty_transcript(client, fake_gnani):
    _, h = new_user(client)
    fake_gnani["transcript"] = ""
    r = client.post("/api/speech/transcribe", headers=h, data={"language_code": "en-IN"},
                    files={"audio": ("a.wav", make_wav(), "audio/wav")})
    assert r.status_code == 422 and r.json()["detail"]["code"] == "empty_transcript"


def test_speak(client, fake_gnani):
    _, h = new_user(client)
    r = client.post("/api/speech/speak", json={"text": "Please give me some water.", "language": "en-IN"}, headers=h)
    assert r.status_code == 200 and r.headers["content-type"].startswith("audio/wav")
    assert r.content[:4] == b"RIFF"
    body = fake_gnani["calls"][-1][1]["json"]
    assert body["model"] == "timbre-v3.0" and body["voice"] == "Shivani"
    assert body["audio_config"]["container"] == "wav" and body["language"] == "en-IN"
    assert client.post("/api/speech/speak", json={"text": "x", "language": "zz"}, headers=h).status_code == 400
    assert client.post("/api/speech/speak", json={"text": "x", "language": "en-IN", "style": "weird"}, headers=h).status_code == 400
    assert client.post("/api/speech/speak", json={"text": "<laugh>", "language": "en-IN"}, headers=h).status_code == 400  # tags only


def test_voice_is_model_specific(client, fake_gnani):
    _, h = new_user(client)
    v = client.get("/api/speech/voices", headers=h).json()
    assert v["model"] == "timbre-v3.0" and len(v["voices"]) == 13 and "Nalini" not in v["voices"]
    # an old timbre-v2.5 voice saved on a phone must not break timbre-v3.0 requests
    client.post("/api/speech/speak", json={"text": "old voice", "language": "mr-IN", "voice": "Nalini"}, headers=h)
    assert fake_gnani["calls"][-1][1]["json"]["voice"] == "Shivani"
    client.post("/api/speech/speak", json={"text": "chosen voice", "language": "mr-IN", "voice": "Avinash"}, headers=h)
    assert fake_gnani["calls"][-1][1]["json"]["voice"] == "Avinash"


def test_audio_tag_styles(client, fake_gnani):
    _, h = new_user(client)
    def spoken(style, text="I need help. Please come now."):
        client.post("/api/speech/speak", json={"text": text, "language": "en-IN", "style": style}, headers=h)
        return fake_gnani["calls"][-1][1]["json"]
    assert spoken("urgent")["text"].startswith("<shouting> ")
    assert spoken("comfort")["text"].startswith("<whispers> ")
    slow = spoken("slow_clear")
    assert slow["text"].startswith("<slow> ") and "<pauses>" in slow["text"] and slow["speed"] == 0.85
    # a user cannot inject their own tags into plain text
    assert "<" not in spoken("normal", "hello <shouting> there")["text"]


def test_speed_clamped_and_cached(client, fake_gnani):
    _, h = new_user(client)
    client.post("/api/speech/speak", json={"text": "hello there", "language": "en-IN", "speed": 5}, headers=h)
    assert fake_gnani["calls"][-1][1]["json"]["speed"] == 1.15
    n = len(fake_gnani["calls"])
    client.post("/api/speech/speak", json={"text": "hello there", "language": "en-IN", "speed": 5}, headers=h)
    assert len(fake_gnani["calls"]) == n  # served from the in-memory cache


def test_daily_limit(client, fake_gnani):
    _, h = new_user(client)
    codes = [client.post("/api/speech/speak", json={"text": f"t{i}", "language": "en-IN"}, headers=h).status_code for i in range(8)]
    assert codes[:6] == [200] * 6 and codes[6] == 429


def test_phrases_crud_and_matching(client):
    uid, h = new_user(client)
    body = {"alias": "Mala pani pahije", "alternatives": ["Pani hava aahe", "I need water"],
            "canonical_meaning": "The user needs drinking water.", "intent": "request_water", "language": "mr-IN"}
    r = client.post("/api/phrases", json=body, headers=h)
    assert r.status_code == 201
    pid = r.json()["id"]
    assert client.post("/api/phrases", json=body, headers=h).status_code == 409
    assert len(client.get("/api/phrases?q=hava", headers=h).json()) == 1
    m = client.post("/api/speech/interpret", json={"text": "pani hava aahe", "language": "mr-IN"}, headers=h).json()
    assert m["source"] == "phrase" and m["matched_phrase_id"] == pid
    neg = client.post("/api/speech/interpret", json={"text": "pani nako", "language": "mr-IN"}, headers=h).json()
    assert neg["source"] != "phrase" and neg["negated"]
    body["alias"] = "Pani de"
    assert client.put(f"/api/phrases/{pid}", json=body, headers=h).json()["alias"] == "Pani de"
    # another user cannot see or delete it
    _, h2 = new_user(client, "Other")
    assert client.get("/api/phrases", headers=h2).json() == []
    assert client.delete(f"/api/phrases/{pid}", headers=h2).status_code == 404
    assert client.delete(f"/api/phrases/{pid}", headers=h).status_code == 200


def test_confirm_and_history(client):
    _, h = new_user(client)
    r = client.post("/api/interpret/confirm", headers=h, json={
        "transcript": "Hello I need water please", "intent": "request_water",
        "canonical_meaning": "The user needs drinking water.", "language": "en-IN",
        "save_as_phrase": True, "final_text": "Please give me some water.", "output_language": "en-IN"})
    assert r.json()["confirmed"] is True and r.json()["phrase_saved"] is True
    r2 = client.post("/api/interpret/confirm", headers=h, json={
        "transcript": "another", "intent": "request_food", "canonical_meaning": "x", "language": "en-IN"})
    assert r2.json()["phrase_saved"] is False  # nothing is learned without approval
    assert len(client.get("/api/phrases", headers=h).json()) == 1
    hist = client.get("/api/history", headers=h).json()
    assert len(hist) == 2
    assert client.get("/api/history?q=water", headers=h).json()[0]["final_text"] == "Please give me some water."
    hid = hist[0]["id"]
    assert client.get(f"/api/history/{hid}", headers=h).status_code == 200
    assert client.delete(f"/api/history/{hid}", headers=h).status_code == 200
    assert client.delete("/api/history", headers=h).json()["deleted"] == 1
    # history can be switched off
    client.put("/api/users/me", json={"save_history": False}, headers=h)
    assert client.post("/api/history", headers=h, json={"final_text": "hi"}).json()["saved"] is False


def test_caregiver_consent(client):
    uid, h = new_user(client, "Person")
    cid, ch = new_user(client, "Caregiver")
    client.post("/api/history", headers=h, json={"final_text": "I need water", "meaning": "water"})
    client.put("/api/users/me", headers=h, json={"emergency_name": "Son", "emergency_phone": "+91 99999 99999"})
    code = client.post("/api/caregiver/code", headers=h).json()["code"]
    assert client.post("/api/caregiver/pair", headers=ch, json={"code": "000000"}).status_code == 400
    assert client.post("/api/caregiver/pair", headers=ch, json={"code": code}).status_code == 200
    assert client.post("/api/caregiver/pair", headers=ch, json={"code": code}).status_code == 400  # single use
    # paired, but NOTHING shared yet
    assert client.get(f"/api/caregiver/people/{uid}/history", headers=ch).json() == []
    assert client.get(f"/api/caregiver/people/{uid}/phrases", headers=ch).status_code == 403
    assert client.get(f"/api/caregiver/people/{uid}/emergency", headers=ch).status_code == 403
    # person approves emergency contact only
    link = client.get("/api/caregiver/links", headers=h).json()[0]
    client.put(f"/api/caregiver/links/{link['id']}", headers=h, json={"share_emergency": True})
    assert client.get(f"/api/caregiver/people/{uid}/emergency", headers=ch).json()["name"] == "Son"
    assert client.get(f"/api/caregiver/people/{uid}/phrases", headers=ch).status_code == 403
    # person shares one message
    hid = client.get("/api/history", headers=h).json()[0]["id"]
    client.post(f"/api/history/{hid}/share", headers=h, json={"shared": True})
    assert len(client.get(f"/api/caregiver/people/{uid}/history", headers=ch).json()) == 1
    # a stranger has no access
    _, sh = new_user(client, "Stranger")
    assert client.get(f"/api/caregiver/people/{uid}/history", headers=sh).status_code == 403
    # revoke
    client.delete(f"/api/caregiver/links/{link['id']}", headers=h)
    assert client.get(f"/api/caregiver/people/{uid}/history", headers=ch).status_code == 403


def test_delete_account(client):
    _, h = new_user(client)
    assert client.delete("/api/users/me", headers=h).status_code == 200
    assert client.get("/api/users/me", headers=h).status_code == 401


def test_intents_catalog(client):
    _, h = new_user(client)
    j = client.get("/api/intents", headers=h).json()
    assert len(j["intents"]) >= 18 and "basic_needs" in j["categories"]


def test_indic_auto_returns_detected_language(client, fake_gnani):
    _, h = new_user(client)
    fake_gnani["transcript"] = "mala pani pahije"
    fake_gnani["detected"] = "mr-IN"
    r = client.post("/api/speech/transcribe", headers=h, data={"language_code": "indic-auto", "output_language": "hi-IN"},
                    files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")}).json()
    assert fake_gnani["calls"][-1][1]["data"]["language_code"] == "indic-auto"
    assert r["detected_language_code"] == "mr-IN" and r["language_code"] == "mr-IN"
    assert r["interpretation"]["intent"] == "request_water"
    assert "पानी" in r["interpretation"]["suggested_message"]  # spoken in Hindi, as requested
    fake_gnani["detected"] = "ta-IN"
    t = client.post("/api/speech/transcribe", headers=h, data={"language_code": "indic-auto"},
                    files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")}).json()
    assert "ta-IN" in (t["interpretation"]["warning"] or "")


def test_marathi_sends_only_mr_IN(client, fake_gnani):
    _, h = new_user(client)
    client.post("/api/speech/transcribe", headers=h, data={"language_code": "mr-IN"},
                files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")})
    assert fake_gnani["calls"][-1][1]["data"]["language_code"] == "mr-IN"


def test_keyterms_from_saved_phrases(client, fake_gnani):
    _, h = new_user(client, "Asha")
    client.post("/api/phrases", headers=h, json={"alias": "Aaji chahaa", "alternatives": ["chaha de"],
                "canonical_meaning": "The user wants tea.", "intent": "request_food", "language": "mr-IN"})
    client.put("/api/users/me", headers=h, json={"emergency_name": "Rohan"})
    audio = {"audio": ("rec.wav", make_wav(1.0), "audio/wav")}
    r = client.post("/api/speech/transcribe", headers=h, data={"language_code": "mr-IN"}, files=audio).json()
    import json
    sent = json.loads(fake_gnani["calls"][-1][1]["data"]["keyterms"])
    assert {"Asha", "Rohan", "Aaji chahaa", "chaha de"} <= set(sent) and r["keyterms_sent"] == len(sent)
    # A/B switch: hints can be turned off per request
    client.post("/api/speech/transcribe", headers=h, data={"language_code": "mr-IN", "use_keyterms": "false"},
                files={"audio": ("rec.wav", make_wav(1.0), "audio/wav")})
    assert "keyterms" not in fake_gnani["calls"][-1][1]["data"]


def test_keyterms_limits():
    from app import gnani
    cleaned = gnani.clean_keyterms([f"word{i}" for i in range(300)] + ["<|bad|>", "word1", "  "], 200)
    assert len(cleaned) == 200 and all("<|" not in c and "|>" not in c for c in cleaned)


def test_stt_language_validation():
    import pytest
    from app import gnani
    assert gnani.normalize_stt_language("hi-IN, en-IN") == "hi-IN,en-IN"
    assert gnani.normalize_stt_language("indic-auto") == "indic-auto"
    for bad in ("hi", "hi-in", "as-IN", "hi-IN,en-IN,mr-IN,ta-IN,te-IN"):
        with pytest.raises(gnani.GnaniError):
            gnani.normalize_stt_language(bad)


def test_speak_stream(client, monkeypatch):
    import base64, json
    from app import gnani
    pcm = b"\x01\x00" * 2000
    lines = []
    for i in range(2):
        lines += ["event: chunk", "data: " + json.dumps({"chunk_index": i, "audio": base64.b64encode(pcm).decode(), "is_final": False}), ""]
    lines += ["event: complete", "data: " + json.dumps({"chunk_index": 2, "audio": "", "is_final": True}), ""]

    class StreamResp:
        status_code = 200
        headers = {"Content-Type": "text/event-stream"}
        def iter_lines(self, decode_unicode=True):
            return iter(lines)

    seen = {}
    def fake_post(url, **kw):
        seen["url"], seen["json"] = url, kw["json"]
        return StreamResp()
    monkeypatch.setattr(gnani.requests, "post", fake_post)
    _, h = new_user(client)
    r = client.post("/api/speech/speak/stream", json={"text": "hello there", "language": "en-IN", "style": "urgent"}, headers=h)
    assert r.status_code == 200 and r.content[:4] == b"RIFF"
    assert len(r.content) == 44 + 2 * len(pcm)
    assert seen["url"].endswith("/tts/sse") and seen["json"]["audio_config"]["container"] == "raw"
    assert seen["json"]["text"].startswith("<shouting>")


def test_speak_stream_error_is_clean_json(client, monkeypatch):
    from app import gnani
    class Bad:
        status_code = 503
        text = "down"
    monkeypatch.setattr(gnani.requests, "post", lambda url, **kw: Bad())
    _, h = new_user(client)
    r = client.post("/api/speech/speak/stream", json={"text": "hello", "language": "en-IN"}, headers=h)
    assert r.status_code == 503 and r.json()["detail"]["code"] == "service_unavailable"


def test_global_daily_cap(client, fake_gnani, monkeypatch):
    from app.config import settings
    monkeypatch.setattr(settings, "global_daily_tts_limit", 1)
    _, h = new_user(client)
    r = client.post("/api/speech/speak", json={"text": "global cap check", "language": "en-IN"}, headers=h)
    assert r.status_code == 429 and r.json()["detail"]["code"] == "service_limit"


def test_health_reports_models(client):
    j = client.get("/health").json()
    assert j["stt_model"] == "prisma-v3.0" and j["tts_model"] == "timbre-v3.0" and "access_code_required" in j


def test_translate_romanised_marathi_to_english_and_back(client):
    _, h = new_user(client)
    r = client.post("/api/translate", headers=h, json={"text": "mla pani have ahe", "target_language": "en-IN"}).json()
    assert r["text"] == "Please give me some water." and r["method"] == "catalog"
    r = client.post("/api/translate", headers=h, json={"text": "I need water", "target_language": "mr-IN"}).json()
    assert "पाणी" in r["text"] and r["method"] == "catalog"
    r = client.post("/api/translate", headers=h, json={"text": "मुझे दवा चाहिए", "target_language": "hi-IN"}).json()
    assert r["method"] == "same" and r["text"] == "मुझे दवा चाहिए"


def test_translate_uses_intent_hint_and_keeps_negation(client):
    _, h = new_user(client)
    r = client.post("/api/translate", headers=h, json={"text": "मला पाणी नको आहे.", "target_language": "en-IN", "intent": "request_water"}).json()
    assert r["text"] == "I do not want water."


def test_translate_free_text_uses_machine_service_and_flags_it(client, monkeypatch):
    from app import translate as tr
    seen = {}
    class R:
        status_code = 200
        def json(self):
            return {"responseData": {"translatedText": "मुझे चाय और बिस्कुट चाहिए"}, "responseStatus": 200}
    def fake_get(url, params=None, timeout=None):
        seen.update(params)
        return R()
    monkeypatch.setattr(tr.requests, "get", fake_get)
    _, h = new_user(client)
    r = client.post("/api/translate", headers=h, json={"text": "I want tea and biscuits please", "target_language": "hi-IN"}).json()
    assert seen["langpair"] == "en|hi" and r["method"] == "machine" and "check" in r["note"].lower()


def test_translate_failure_is_honest(client, monkeypatch):
    from app import translate as tr
    monkeypatch.setattr(tr.settings, "translate_fallback", False)
    _, h = new_user(client)
    r = client.post("/api/translate", headers=h, json={"text": "I want tea and biscuits please", "target_language": "mr-IN"}).json()
    assert r["method"] == "failed" and r["note"]
    assert client.post("/api/translate", headers=h, json={"text": "hi", "target_language": "ta-IN"}).status_code == 400
