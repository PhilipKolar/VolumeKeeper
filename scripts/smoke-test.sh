#!/usr/bin/env bash
# VolumeKeeper pre-commit / pre-build smoke suite.
#
#   ./scripts/smoke-test.sh                 # all phases (device probe needs the Pixel on adb)
#   SKIP_DEVICE=1 ./scripts/smoke-test.sh   # no phone
#   SKIP_KOTLIN=1 ...                       # skip the Gradle core tests (CI runs them in their own job)
#   VK_PKG=com.philkolar.volumekeeper.dev   # probe the dev variant instead
#
# Phases: 1 typecheck · 2 TS unit tests · 3 Kotlin core tests · 4 stray-hex lint ·
#         5 core-purity lint · 6 device probe
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_DIR="$REPO_ROOT/app"
CORE_DIR="$APP_DIR/modules/volume-keeper/android/src/main/java/com/philkolar/volumekeeper/core"
CORE_TESTS="$APP_DIR/modules/volume-keeper/core-tests"
PKG="${VK_PKG:-com.philkolar.volumekeeper}"

BLUE='\033[0;34m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
phase() { echo; echo -e "${BLUE}==> $1${NC}"; }
pass()  { echo -e "${GREEN}    ✓ $1${NC}"; }
fail()  { echo -e "${RED}    ✗ $1${NC}" >&2; exit 1; }
skip()  { echo -e "${YELLOW}    ~ $1${NC}"; }
warn()  { echo -e "${YELLOW}    ! $1${NC}"; }

if [[ -s "$HOME/.nvm/nvm.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.nvm/nvm.sh" >/dev/null 2>&1 && nvm use 22 >/dev/null 2>&1 || true
fi

phase "1/6  Typecheck (tsc --noEmit)"
cd "$APP_DIR"
if npx --no-install tsc --noEmit; then pass "TypeScript compiles"; else fail "TypeScript errors"; fi

phase "2/6  TS unit tests (node:test via tsx)"
if npm test --silent; then pass "Tests pass"; else fail "Tests failed"; fi

phase "3/6  Kotlin core tests (JVM, core-tests/)"
if [[ "${SKIP_KOTLIN:-0}" == "1" ]]; then
  skip "SKIP_KOTLIN=1"
else
  if [[ -z "${JAVA_HOME:-}" && -d "$HOME/.jdks/temurin-21" ]]; then export JAVA_HOME="$HOME/.jdks/temurin-21"; fi
  if (cd "$CORE_TESTS" && ./gradlew test --no-daemon -q); then pass "Core tests pass"; else fail "Core tests failed"; fi
fi

phase "4/6  Stray colour literals (only lib/theme.ts may contain hex)"
STRAY=$(grep -rEn --include='*.ts' --include='*.tsx' '#[0-9a-fA-F]{3,8}\b' "$APP_DIR/app" "$APP_DIR/components" "$APP_DIR/hooks" "$APP_DIR/lib" 2>/dev/null \
  | grep -v 'lib/theme.ts' | grep -v 'brand-color' || true)
if [[ -z "$STRAY" ]]; then pass "No stray hex literals"; else echo "$STRAY"; fail "Hex literals outside lib/theme.ts (add to theme, or mark // brand-color)"; fi

phase "5/6  Core purity (no android./androidx./expo. imports in core/)"
IMPURE=$(grep -rEn '^import (android|androidx|expo)\.' "$CORE_DIR" || true)
if [[ -z "$IMPURE" ]]; then pass "core/ is pure Kotlin"; else echo "$IMPURE"; fail "core/ must not depend on Android or Expo"; fi

phase "6/6  Device probe ($PKG)"
if [[ "${SKIP_DEVICE:-0}" == "1" ]]; then
  skip "SKIP_DEVICE=1"
elif ! adb get-state >/dev/null 2>&1; then
  fail "No adb device (connect the Pixel over wireless debugging, or SKIP_DEVICE=1)"
else
  WANT=$(node -p "require('$APP_DIR/app.json').expo.version")
  HAVE=$(adb shell dumpsys package "$PKG" | grep -m1 versionName | sed 's/.*versionName=//' | tr -d '\r')
  if [[ -z "$HAVE" ]]; then warn "$PKG not installed"; else
    if [[ "$HAVE" == "$WANT" ]]; then pass "installed $HAVE matches app.json"; else warn "installed $HAVE, app.json says $WANT"; fi
    if adb shell dumpsys notification | sed -n '/Live notification listeners/,/^ *[A-Z][a-z]* [a-z]*:$/p' | grep -q "ComponentInfo{$PKG/"; then
      pass "notification listener bound"
    else
      warn "notification listener NOT bound (access not granted, or app never started)"
    fi
    PROC=$(adb shell dumpsys activity processes "$PKG" | grep -m1 -E 'curProcState=' | tr -d '\r' | sed 's/^ *//')
    CAP=$(adb shell dumpsys activity processes "$PKG" | grep -m1 -E 'curCapability=' | tr -d '\r' | sed 's/^ *//')
    FROZEN=$(adb shell dumpsys activity processes "$PKG" | grep -m1 -E 'isFrozen=' | tr -d '\r' | sed 's/^ *//')
    echo "    $PROC"; echo "    $CAP"; echo "    $FROZEN"
    if adb shell dumpsys audio | grep -q "AudioHardening.*ignored for $PKG "; then
      warn "AudioHardening has logged volume calls from $PKG (see scripts/vk.sh state)"
    else
      pass "no AudioHardening entries for $PKG"
    fi
  fi
fi

echo; echo -e "${GREEN}All smoke tests passed.${NC}"
