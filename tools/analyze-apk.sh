#!/usr/bin/env bash
# analyze-apk.sh — one-shot static analysis of an APK.
#
#   1. REA: identify_flutter_build + inspect_dart_aot (no JDK needed)
#   2. tools/apk-triage.py: manifest, signing, permissions, DEX, SDKs, endpoints
#   3. apkid (optional): packer / obfuscator identification
#
# Usage: tools/analyze-apk.sh TARGET.apk [OUTDIR]
#
# Dependencies are bootstrapped into a venv on first run (androguard). REA is
# used when `rea` is on PATH (`npm i -g rea-agents`); it is optional.
set -euo pipefail

APK=${1:?usage: analyze-apk.sh TARGET.apk [OUTDIR]}
APK=$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")
[ -f "$APK" ] || { echo "error: $APK not found" >&2; exit 2; }
OUT=${2:-"$(pwd)/apk-analysis"}
mkdir -p "$OUT"

VENV=${APK_TRIAGE_VENV:-/tmp/rea-apk-venv}
if [ ! -x "$VENV/bin/python" ]; then
  echo "[0/4] bootstrapping $VENV"
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
  "$VENV/bin/pip" install --quiet androguard apkid
fi
PYTHON="$VENV/bin/python"

echo "[1/4] fingerprint"
sha256sum "$APK" | awk '{print "  sha256 " $1}'
stat -c '  bytes %s' "$APK"

echo "[2/4] REA identify-flutter-build"
if command -v rea >/dev/null 2>&1; then
  if rea identify-flutter-build "$APK" --json > "$OUT/rea-flutter.json" 2>"$OUT/rea-flutter.err"; then
    grep -q '"flutter_detected":true' "$OUT/rea-flutter.json" && FLUTTER=1 || FLUTTER=0
    echo "  flutter_detected=$FLUTTER"
    if [ "$FLUTTER" = 1 ]; then
      echo "  inspecting the Dart AOT snapshot"
      rea inspect-dart-aot "$APK" --json > "$OUT/rea-dart-aot.json" 2>"$OUT/rea-dart-aot.err" \
        || echo "  (AOT inspection unavailable, see $OUT/rea-dart-aot.err)"
    fi
  else
    echo "  (REA failed, see $OUT/rea-flutter.err)"
  fi
else
  echo "  rea not installed — skipping (npm i -g rea-agents)"
fi

echo "[3/4] apk-triage.py"
"$PYTHON" "$(dirname "$0")/apk-triage.py" "$APK" --json "$OUT/triage.json" --md "$OUT/triage.md"

echo "[4/4] apkid"
"$VENV/bin/apkid" -j "$APK" > "$OUT/apkid.json" 2>/dev/null || echo "  (apkid reported nothing)"

echo
echo "report: $OUT/triage.md"
echo "raw:    $OUT/triage.json"
