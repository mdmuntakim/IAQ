#!/usr/bin/env bash
# ============================================================================
# IAQ -- build script
# ----------------------------------------------------------------------------
# Pure JDK build: no Gradle, no Maven, no network.  Produces a single
# executable JAR that runs on any machine with Java 17 or newer.
#
#   bash build.sh                 # tests + build/IAQ.jar
#   bash build.sh --skip-tests    # JAR only
#   bash build.sh --tests         # JVM tests only
#
# Environment:
#   IAQ_VERSION        version name (default 1.0.0)
#   JAVA_HOME          JDK location (17+)
#
# Output: build/IAQ.jar
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

VERSION="${IAQ_VERSION:-1.0.0}"
OUT="$ROOT/build"
CLASSES="$OUT/classes"
TEST_CLASSES="$OUT/test-classes"
RUN_TESTS=1
ONLY_TESTS=0

for arg in "$@"; do
  case "$arg" in
    --skip-tests) RUN_TESTS=0 ;;
    --tests) ONLY_TESTS=1 ;;
    -h|--help) sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

command -v javac >/dev/null 2>&1 || die "javac not found; install a JDK 17+"
JAVAC_FLAGS=(--release 17 -encoding UTF-8 -nowarn -Xlint:-options)

MAIN_SOURCES=()
while IFS= read -r f; do MAIN_SOURCES+=("$f"); done < <(find src/main/java -name '*.java' | sort)
TEST_SOURCES=()
while IFS= read -r f; do TEST_SOURCES+=("$f"); done < <(find src/test/java -name '*.java' 2>/dev/null | sort)
[ "${#MAIN_SOURCES[@]}" -gt 0 ] || die "no sources in src/main/java"

rm -rf "$CLASSES" "$TEST_CLASSES"
mkdir -p "$CLASSES" "$TEST_CLASSES"

run_tests() {
  log "compiling engine (${#MAIN_SOURCES[@]} files)"
  javac "${JAVAC_FLAGS[@]}" -d "$TEST_CLASSES" "${MAIN_SOURCES[@]}"
  log "compiling tests (${#TEST_SOURCES[@]} files)"
  javac "${JAVAC_FLAGS[@]}" -cp "$TEST_CLASSES" -d "$TEST_CLASSES" "${TEST_SOURCES[@]}"
  log "running tests"
  java -cp "$TEST_CLASSES" iaq.TestMain
}

if [ "$RUN_TESTS" = "1" ] || [ "$ONLY_TESTS" = "1" ]; then
  run_tests
fi
[ "$ONLY_TESTS" = "1" ] && exit 0

log "compiling IAQ.jar"
javac "${JAVAC_FLAGS[@]}" -d "$CLASSES" "${MAIN_SOURCES[@]}"

MANIFEST="$OUT/MANIFEST.MF"
mkdir -p "$OUT"
{
  echo "Manifest-Version: 1.0"
  echo "Main-Class: iaq.Main"
  echo "Implementation-Title: IAQ"
  echo "Implementation-Version: $VERSION"
  echo "Created-By: IAQ build.sh"
} > "$MANIFEST"

jar --create --file "$OUT/IAQ.jar" --manifest "$MANIFEST" -C "$CLASSES" .
SIZE="$(du -h "$OUT/IAQ.jar" | cut -f1)"
log "built $OUT/IAQ.jar ($SIZE)"
printf 'JAR=%s\n' "$OUT/IAQ.jar"
