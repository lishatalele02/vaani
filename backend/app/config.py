"""Configuration loaded from environment variables (backend/.env)."""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def _env(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


def _json_env(name: str, default: dict) -> dict:
    raw = _env(name)
    if not raw:
        return default
    try:
        value = json.loads(raw)
        return value if isinstance(value, dict) else default
    except json.JSONDecodeError:
        return default


# Timbre voices differ per model and cannot be mixed (docs.gnani.ai TTS inference).
# timbre-v3.0: 13 voices, each speaks all 10 languages (incl. Marathi) and supports audio tags.
V3_VOICES = ["Avinash", "Shivani", "Lakshmi", "Kalpana", "Shubham", "Prakash", "Devansh",
             "Yashraj", "Chetana", "Vasudha", "Riyaa", "Tarika", "Harshad"]
V3_DEFAULT_VOICE = "Shivani"
# timbre-v2.5: Gnani documents 42 voices but only publishes these as examples.
V25_VOICES = ["Nalini", "Kaveri", "Deepak", "Poorvi"]
V25_DEFAULT_VOICE = "Nalini"

# Keys that Prisma v3.0 ignores; dropped from any configured extra fields.
_V3_IGNORED_STT_FIELDS = {"itn_native_numerals", "bias_score"}


@dataclass
class Settings:
    api_keys: list = field(default_factory=list)
    stt_url: str = "https://api.vachana.ai/stt/v3"
    stt_key_header: str = "X-API-Key-ID"
    stt_key_prefix: str = ""
    stt_audio_field: str = "audio_file"
    stt_language_field: str = "language_code"
    stt_format_field: str = "format"
    stt_format_value: str = "transcribe"
    stt_extra_fields: dict = field(default_factory=dict)
    stt_model: str = "prisma-v3.0"
    keyterms_enabled: bool = True
    keyterms_max: int = 200
    tts_url: str = "https://api.vachana.ai/api/v1/tts/inference"
    tts_key_header: str = "X-API-Key-ID"
    tts_sse_url: str = "https://api.vachana.ai/api/v1/tts/sse"
    tts_voice: str = V3_DEFAULT_VOICE
    tts_voices: list = field(default_factory=lambda: list(V3_VOICES))
    tts_model: str = "timbre-v3.0"
    tts_speed: float = 1.0
    tts_audio_config: dict = field(default_factory=dict)
    gnani_max_concurrency: int = 1
    gnani_timeout: int = 60
    db_path: str = "./samajh_sakhi.sqlite3"
    max_upload_mb: int = 10
    daily_stt_limit: int = 200
    daily_tts_limit: int = 400
    registration_code: str = ""
    require_access_code: bool = False
    translate_fallback: bool = True
    translate_url: str = "https://api.mymemory.translated.net/get"
    daily_translate_limit: int = 300
    global_daily_stt_limit: int = 0   # 0 = no global cap
    global_daily_tts_limit: int = 0

    @property
    def tts_is_v3(self) -> bool:
        return self.tts_model.startswith("timbre-v3")

    @property
    def stt_is_v3(self) -> bool:
        return self.stt_model.startswith("prisma-v3")

    @property
    def max_upload_bytes(self) -> int:
        return self.max_upload_mb * 1024 * 1024


def load_settings() -> Settings:
    keys = [k.strip() for k in _env("GNANI_API_KEYS").split(",") if k.strip()]
    single = _env("GNANI_API_KEY")
    if single and single not in keys:
        keys.insert(0, single)
    tts_model = _env("GNANI_TTS_MODEL", "timbre-v3.0")
    is_v3 = tts_model.startswith("timbre-v3")
    default_voices = V3_VOICES if is_v3 else V25_VOICES
    voices = [v.strip() for v in _env("GNANI_TTS_VOICES", ",".join(default_voices)).split(",") if v.strip()]
    if is_v3:  # voices are model specific: never offer a v2.5 voice to timbre-v3.0
        voices = [v for v in voices if v in V3_VOICES] or list(V3_VOICES)
    voice = _env("GNANI_TTS_VOICE", V3_DEFAULT_VOICE if is_v3 else V25_DEFAULT_VOICE)
    if voice not in voices:
        voice = voices[0]
    stt_extra = _json_env("GNANI_STT_EXTRA_FIELDS_JSON", {"model": "prisma-v3.0"})
    stt_model = str(stt_extra.get("model", "prisma-v3.0"))
    if stt_model.startswith("prisma-v3"):
        stt_extra = {k: v for k, v in stt_extra.items() if k not in _V3_IGNORED_STT_FIELDS}
    db_path = _env("SAMAJH_DB_PATH", "./samajh_sakhi.sqlite3")
    if not os.path.isabs(db_path):
        db_path = str((BASE_DIR / db_path).resolve())
    return Settings(
        api_keys=keys,
        stt_url=_env("GNANI_STT_URL", "https://api.vachana.ai/stt/v3"),
        stt_key_header=_env("GNANI_STT_KEY_HEADER", "X-API-Key-ID"),
        stt_key_prefix=os.getenv("GNANI_STT_KEY_PREFIX", ""),
        stt_audio_field=_env("GNANI_STT_AUDIO_FIELD", "audio_file"),
        stt_language_field=_env("GNANI_STT_LANGUAGE_FIELD", "language_code"),
        stt_format_field=_env("GNANI_STT_FORMAT_FIELD", "format"),
        stt_format_value=_env("GNANI_STT_FORMAT_VALUE", "transcribe"),
        stt_extra_fields=stt_extra,
        stt_model=stt_model,
        keyterms_enabled=_env("GNANI_KEYTERMS", "true").lower() not in ("0", "false", "no", "off"),
        keyterms_max=max(0, min(200, int(_env("GNANI_KEYTERMS_MAX", "200") or 200))),
        tts_url=_env("GNANI_TTS_URL", "https://api.vachana.ai/api/v1/tts/inference"),
        tts_key_header=_env("GNANI_TTS_KEY_HEADER", "X-API-Key-ID"),
        tts_sse_url=_env("GNANI_TTS_SSE_URL", "https://api.vachana.ai/api/v1/tts/sse"),
        tts_voice=voice,
        tts_voices=voices,
        tts_model=tts_model,
        tts_speed=float(_env("GNANI_TTS_SPEED", "1.0") or 1.0),
        tts_audio_config=_json_env(
            "GNANI_TTS_AUDIO_CONFIG_JSON",
            {"sample_rate": 48000, "num_channels": 1, "sample_width": 2, "encoding": "linear_pcm", "container": "wav"},
        ),
        gnani_max_concurrency=max(1, int(_env("GNANI_MAX_CONCURRENCY", "1") or 1)),
        gnani_timeout=int(_env("GNANI_TIMEOUT_SECONDS", "60") or 60),
        db_path=db_path,
        max_upload_mb=int(_env("MAX_UPLOAD_MB", "10") or 10),
        daily_stt_limit=int(_env("DAILY_STT_LIMIT", "200") or 200),
        daily_tts_limit=int(_env("DAILY_TTS_LIMIT", "400") or 400),
        registration_code=_env("REGISTRATION_CODE"),
        translate_fallback=_env("TRANSLATE_FALLBACK", "true").lower() not in ("0", "false", "no", "off"),
        translate_url=_env("TRANSLATE_URL", "https://api.mymemory.translated.net/get"),
        daily_translate_limit=int(_env("DAILY_TRANSLATE_LIMIT", "300") or 300),
        require_access_code=_env("REQUIRE_ACCESS_CODE", "false").lower() in ("1", "true", "yes", "on"),
        global_daily_stt_limit=int(_env("GLOBAL_DAILY_STT_LIMIT", "0") or 0),
        global_daily_tts_limit=int(_env("GLOBAL_DAILY_TTS_LIMIT", "0") or 0),
    )


settings = load_settings()
