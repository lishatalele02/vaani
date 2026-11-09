"""LIVE check of your Gnani credentials (uses a few API credits).

Run from the backend folder after putting your key in .env:
    python scripts/check_gnani.py

1) Timbre TTS: speaks a sentence and saves it as check_tts.wav
2) Prisma STT: sends that WAV back and prints the transcript and detected language
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import gnani  # noqa: E402
from app.config import settings  # noqa: E402
from app.wavfix import describe  # noqa: E402


def main() -> int:
    if not settings.api_keys:
        print("No key found. Put GNANI_API_KEY=... in backend/.env first.")
        return 1
    print(f"Using {len(settings.api_keys)} key(s). STT model={settings.stt_model}; TTS model={settings.tts_model}, voice={settings.tts_voice}")
    try:
        audio, ctype = gnani.synthesize("Hello, I can help you communicate.", "en-IN")
    except gnani.GnaniError as e:
        print(f"TTS FAILED: {e.code} - {e.detail or e.message}")
        return 2
    Path("check_tts.wav").write_bytes(audio)
    print("Audio format:", describe(audio))
    print(f"TTS OK: {len(audio)} bytes ({ctype}) saved to check_tts.wav - play it to hear the voice.")
    try:
        heard = gnani.transcribe(audio, "check_tts.wav", "audio/wav", "indic-auto")
    except gnani.GnaniError as e:
        print(f"STT FAILED: {e.code} - {e.detail or e.message}")
        return 3
    print(f"STT OK: transcript = {heard.text!r}, detected_language_code = {heard.detected_language!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
