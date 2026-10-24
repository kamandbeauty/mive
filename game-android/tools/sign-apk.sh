#!/usr/bin/env bash
# Sign the release APK produced by Gradle and drop it at ./Mivehchin-<version>.apk
# Usage: sign-apk.sh <version>   (version must already be filesystem-safe)
set -euo pipefail

VERSION="${1:?version required}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
APK_DIR="app/build/outputs/apk/release"

echo "== sdk: $SDK"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
if [ -z "$BT" ]; then
    echo "!! no build-tools found under $SDK/build-tools" >&2
    ls -1 "$SDK" >&2 || true
    exit 1
fi
echo "== build-tools: $BT"

echo "== build outputs:"
find app/build/outputs -name '*.apk' -printf '%p %s bytes\n' 2>/dev/null || true

UNSIGNED="$APK_DIR/app-release-unsigned.apk"
if [ ! -f "$UNSIGNED" ]; then
    UNSIGNED="$(find app/build/outputs/apk -name '*.apk' | head -1 || true)"
fi
if [ -z "$UNSIGNED" ] || [ ! -f "$UNSIGNED" ]; then
    echo "!! no APK to sign" >&2
    exit 1
fi
echo "== signing $UNSIGNED"

OUT="$(pwd)/../Mivehchin-${VERSION}.apk"
ALIGNED="$(pwd)/../.aligned-${VERSION}.apk"

keytool -genkeypair -noprompt -keystore miveh.jks -alias miveh \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass mivehchin -keypass mivehchin \
    -dname "CN=Mivehchin, O=Mivehchin, C=IR"

"$BT/zipalign" -p -f 4 "$UNSIGNED" "$ALIGNED"
"$BT/apksigner" sign --ks miveh.jks --ks-pass pass:mivehchin \
    --key-pass pass:mivehchin --out "$OUT" "$ALIGNED"
rm -f "$ALIGNED"
"$BT/apksigner" verify --print-certs "$OUT"
ls -l "$OUT"
echo "== signed: $OUT"
