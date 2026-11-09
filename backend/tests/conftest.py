import io
import os
import sys
import tempfile
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
_tmp = tempfile.mkdtemp()
os.environ["SAMAJH_DB_PATH"] = os.path.join(_tmp, "test.sqlite3")
os.environ["GNANI_API_KEY"] = "test-key-not-real"
os.environ["MAX_UPLOAD_MB"] = "1"
os.environ["DAILY_TTS_LIMIT"] = "6"

import pytest
from fastapi.testclient import TestClient

from app import gnani
from app.main import app


def make_wav(seconds: float = 1.0, rate: int = 16000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x01\x00" * int(rate * seconds))
    return buf.getvalue()


class FakeResp:
    def __init__(self, status=200, json_data=None, content=b"", headers=None, text=""):
        self.status_code = status
        self._json = json_data
        self.content = content
        self.headers = headers or {}
        self.text = text or (str(json_data) if json_data else "")

    def json(self):
        if self._json is None:
            raise ValueError("no json")
        return self._json


@pytest.fixture()
def fake_gnani(monkeypatch):
    """Replace the HTTP call to Gnani. NOT a live Gnani test."""
    state = {"transcript": "Hello I need water please", "calls": [], "status": 200, "detected": "en-IN"}

    def fake_post(url, **kw):
        state["calls"].append((url, kw))
        if state["status"] != 200:
            return FakeResp(state["status"], text="err")
        if "stt" in url:
            return FakeResp(200, {"success": True, "transcript": state["transcript"], "model": "prisma-v3.0",
                                  "detected_language_code": state.get("detected", "hi-IN")})
        return FakeResp(200, content=make_wav(1.0, 48000), headers={"Content-Type": "audio/wav"})

    monkeypatch.setattr(gnani.requests, "post", fake_post)
    gnani._cache.clear()
    return state


@pytest.fixture(autouse=True)
def _reset_sign_up_limits():
    from app import main
    main._reg_log.clear()
    main._pair_fail.clear()
    yield


@pytest.fixture(scope="session")
def client():
    with TestClient(app) as c:
        yield c


def new_user(client, name="Tester"):
    r = client.post("/api/users/register", json={"display_name": name, "preferred_language": "mr-IN"})
    assert r.status_code == 200, r.text
    d = r.json()
    return d["user_id"], {"Authorization": f"Bearer {d['token']}"}
