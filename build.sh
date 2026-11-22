#!/usr/bin/env bash
# ============================================================================
# BXAnimator -- build script
# ----------------------------------------------------------------------------
# Builds the APK with the Android SDK command line tools only (aapt2, javac,
# d8, zipalign, apksigner).  No Gradle, no network, no AndroidX: the app is a
# single plain-Java APK that builds in a few seconds.
#
#   bash build.sh                 # tests + APK
#   bash build.sh --skip-tests    # APK only
#   bash build.sh --tests         # JVM tests only
#
# Environment:
#   ANDROID_HOME / ANDROID_SDK_ROOT   Android SDK location
#   BX_VERSION                        version name  (default 1.0.0)
#   BX_VERSION_CODE                   version code  (default 1)
#
# Output: build/BXAnimator-<version>.apk
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VERSION="${BX_VERSION:-1.0.0}"
VERSION_CODE="${BX_VERSION_CODE:-1}"
OUT="$ROOT/build"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
DEX="$OUT/dex"
TEST_CLASSES="$OUT/test-classes"
RUN_TESTS=1
ONLY_TESTS=0

for arg in "$@"; do
  case "$arg" in
    --skip-tests) RUN_TESTS=0 ;;
    --tests) ONLY_TESTS=1 ;;
    -h|--help) sed -n '3,24p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  for candidate in /usr/local/lib/android/sdk "$HOME/Android/Sdk" "$HOME/android-sdk"; do
    [ -d "$candidate" ] && SDK="$candidate" && break
  done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || die "Android SDK not found; set ANDROID_HOME"
export ANDROID_HOME="$SDK"

BUILD_TOOLS="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
[ -x "$BUILD_TOOLS/aapt2" ] || die "aapt2 not found in $BUILD_TOOLS"

PLATFORM_JAR=""
for api in 36 35 34; do
  if [ -f "$SDK/platforms/android-$api/android.jar" ]; then
    PLATFORM_JAR="$SDK/platforms/android-$api/android.jar"
    break
  fi
done
[ -n "$PLATFORM_JAR" ] || PLATFORM_JAR="$(ls -d "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
[ -f "$PLATFORM_JAR" ] || die "android.jar not found in $SDK/platforms"

log "SDK        $SDK"
log "build tools $BUILD_TOOLS"
log "platform    $PLATFORM_JAR"
log "version     $VERSION ($VERSION_CODE)"

rm -rf "$GEN" "$CLASSES" "$DEX" "$TEST_CLASSES"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$TEST_CLASSES"

JAVAC_FLAGS=(--release 17 -encoding UTF-8 -nowarn -Xlint:-options)

# ------------------------------------------------------------------- JVM tests
CORE_SOURCES=()
while IFS= read -r f; do CORE_SOURCES+=("$f"); done < <(find core/src -name '*.java' | sort)
TEST_SOURCES=()
while IFS= read -r f; do TEST_SOURCES+=("$f"); done < <(find core/test -name '*.java' | sort)

run_tests() {
  log "compiling core (${#CORE_SOURCES[@]} files)"
  javac "${JAVAC_FLAGS[@]}" -d "$TEST_CLASSES" "${CORE_SOURCES[@]}"
  log "compiling tests (${#TEST_SOURCES[@]} files)"
  javac "${JAVAC_FLAGS[@]}" -cp "$TEST_CLASSES" -d "$TEST_CLASSES" "${TEST_SOURCES[@]}"
  log "running JVM tests"
  java -cp "$TEST_CLASSES" app.bxanimator.core.TestMain
}

if [ "$RUN_TESTS" = "1" ] || [ "$ONLY_TESTS" = "1" ]; then
  run_tests
fi
[ "$ONLY_TESTS" = "1" ] && exit 0

# ----------------------------------------------------------------- resources
log "compiling resources"
"$BUILD_TOOLS/aapt2" compile --dir app/res -o "$OUT/res.zip"

log "linking resources"
"$BUILD_TOOLS/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM_JAR" \
  --manifest app/AndroidManifest.xml \
  --java "$GEN" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION" \
  --auto-add-overlay \
  "$OUT/res.zip"

# --------------------------------------------------------------------- compile
APP_SOURCES=()
while IFS= read -r f; do APP_SOURCES+=("$f"); done < <(find app/src -name '*.java' | sort)
R_JAVA="$GEN/app/bxanimator/R.java"
[ -f "$R_JAVA" ] || die "R.java was not generated"

log "compiling app (${#APP_SOURCES[@]} files + ${#CORE_SOURCES[@]} core files)"
javac "${JAVAC_FLAGS[@]}" \
  -classpath "$PLATFORM_JAR" \
  -d "$CLASSES" \
  "$R_JAVA" "${CORE_SOURCES[@]}" "${APP_SOURCES[@]}"

# ------------------------------------------------------------------------ dex
log "dexing"
CLASS_FILES=()
while IFS= read -r f; do CLASS_FILES+=("$f"); done < <(find "$CLASSES" -name '*.class' | sort)
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$PLATFORM_JAR" \
  --output "$DEX" "${CLASS_FILES[@]}"

# -------------------------------------------------------------------- package
log "packaging"
cp "$OUT/base.apk" "$OUT/BXAnimator-unsigned.apk"
( cd "$DEX" && zip -q -j "$OUT/BXAnimator-unsigned.apk" classes*.dex )

# ---------------------------------------------------------------------- sign
KEYSTORE="$ROOT/signing/bxanimator.jks"
KEYPASS="bxanimator"
if [ ! -f "$KEYSTORE" ]; then
  log "creating project signing key"
  mkdir -p "$ROOT/signing"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYPASS" -keypass "$KEYPASS" \
    -alias bxanimator -keyalg RSA -keysize 3072 -validity 12000 \
    -dname "CN=BXAnimator, OU=Animation, O=BXAnimator, C=US" >/dev/null 2>&1
fi

log "aligning"
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/BXAnimator-unsigned.apk" "$OUT/BXAnimator-aligned.apk"

log "signing"
APK="$OUT/BXAnimator-$VERSION.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out "$APK" "$OUT/BXAnimator-aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$APK" >/dev/null 2>&1 || die "signature verification failed"
rm -f "$OUT/BXAnimator-unsigned.apk" "$OUT/BXAnimator-aligned.apk" "$OUT/base.apk" "$OUT/res.zip"

SIZE="$(du -h "$APK" | cut -f1)"
log "built $APK ($SIZE)"
"$BUILD_TOOLS/aapt2" dump badging "$APK" 2>/dev/null | sed -n '1,2p' || true
printf 'APK=%s\n' "$APK"
