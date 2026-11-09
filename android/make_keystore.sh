#!/usr/bin/env bash
# Creates a release keystore + keystore.properties (both git-ignored). Run once, keep a private backup.
set -e
cd "$(dirname "$0")"
read -r -s -p "Choose a keystore password: " PW; echo
keytool -genkeypair -v -keystore samajh-release.jks -alias samajh -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PW" -keypass "$PW" -dname "CN=Samajh Sakhi, O=Samajh Sakhi, C=IN"
cat > keystore.properties <<EOP
storeFile=../samajh-release.jks
storePassword=$PW
keyAlias=samajh
keyPassword=$PW
EOP
echo "Done. Build the signed APK with:"
echo "  ./gradlew assembleRelease -Psamajh.serverUrl=https://YOUR-DOMAIN/"
echo "APK: app/build/outputs/apk/release/app-release.apk"
