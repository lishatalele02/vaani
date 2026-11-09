# Hosting for judges: HTTPS backend + signed APK

Goal: judges install the APK and use the app without your laptop, and your 5,000 Gnani credits cannot be drained.

## 1. Backend over HTTPS (Docker + Caddy)
You need a small server (any VPS) and a domain name pointing at it.

```bash
cd SamajhSakhi/backend
cp .env.example .env
# edit .env:
#   GNANI_API_KEY=...            (your key, only here)
#   REGISTRATION_CODE=choose-a-code-judges-will-type
#   GLOBAL_DAILY_STT_LIMIT / GLOBAL_DAILY_TTS_LIMIT  (server-wide caps, already set to safe defaults)
cd ../deploy
DOMAIN=samajh.example.com docker compose up -d --build
curl https://samajh.example.com/health     # {"status":"ok", ..., "access_code_required": true}
```
* The container sets `REQUIRE_ACCESS_CODE=true`: it refuses to start without an access code.
* Caddy gets and renews the HTTPS certificate on its own.
* The database lives in a Docker volume, not in the image.

Credit protection, in layers: access code to sign up, 20 sign-ups per hour per address, per-user daily limits, server-wide daily caps (`GLOBAL_DAILY_*`), and the app's Quick/offline cards need no credits at all.

## 2. Signed APK
```bash
cd SamajhSakhi/android
./make_keystore.sh                       # once; keep samajh-release.jks and its password safe, never commit them
./gradlew assembleRelease -Psamajh.serverUrl=https://samajh.example.com/
# app/build/outputs/apk/release/app-release.apk
```
Release builds block plain http://, so they only talk to your HTTPS server. Share the APK with the access code in your submission text (not inside the app or the repo).

On first use, ask judges to open Settings → Offline messages → Download for offline use (about 21 short clips, one time).
