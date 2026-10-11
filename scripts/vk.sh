#!/usr/bin/env bash
# adb helpers for testing VolumeKeeper on the Pixel. Default target is the dev variant
# (VK_PKG=com.philkolar.volumekeeper to point at the release app).
#
#   vk.sh fetch [run-id]            download the newest (or given) build-android artifact into ./.apks/
#   vk.sh install <apk>             force-stop, install, print versionName
#   vk.sh grant                     allow the notification listener from adb (approved for spikes)
#   vk.sh state                     version, listener, procstate/capability/frozen, hardening, CSD state, volume
#   vk.sh simulate <order> [gap] [from] [1x|5x]
#                                   order: drop-first | notif-first | drop-only | notif-only (dev only)
#   vk.sh set <n>                   background setStreamVolume + read-back, logged as a "test" event (dev only)
#   vk.sh speaker on|off            treat the phone speaker as headphones (dev only)
#   vk.sh enable on|off             master switch (dev only)
#   vk.sh user <n>                  a user-like change: cmd media_session volume --set n
#   vk.sh vol                       current STREAM_MUSIC index
#   vk.sh log                       VolumeKeeper logcat lines
#
# MacroDroid's "volume lowered" macro must be OFF whenever a VolumeKeeper master switch is on.
set -euo pipefail

PKG="${VK_PKG:-com.philkolar.volumekeeper.dev}"
RECEIVER="$PKG/com.philkolar.volumekeeper.debug.SimulationReceiver"
LISTENER="$PKG/com.philkolar.volumekeeper.service.VkListenerService"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

sim() { adb shell am broadcast -n "$RECEIVER" "$@" >/dev/null && echo "sent: $*"; }

cmd="${1:-state}"; shift || true
case "$cmd" in
  fetch)
    mkdir -p "$REPO_ROOT/.apks"
    RUN="${1:-$(gh run list --workflow=build-android.yml --status success --limit 1 --json databaseId -q '.[0].databaseId')}"
    gh run download "$RUN" --dir "$REPO_ROOT/.apks/$RUN"
    find "$REPO_ROOT/.apks/$RUN" -name '*.apk'
    ;;
  install)
    APK="${1:?apk path}"
    case "$APK" in *-dev-*) P=com.philkolar.volumekeeper.dev ;; *) P=com.philkolar.volumekeeper ;; esac
    adb shell am force-stop "$P" || true
    adb install -r "$APK"
    adb shell dumpsys package "$P" | grep -m1 versionName
    ;;
  grant)
    adb shell cmd notification allow_listener "$LISTENER"
    echo "allowed $LISTENER"
    ;;
  state)
    echo "== $PKG"
    adb shell dumpsys package "$PKG" | grep -m1 versionName || echo "  not installed"
    if adb shell dumpsys notification | grep -q "ComponentInfo{$LISTENER}"; then echo "  listener: bound"; else echo "  listener: NOT bound"; fi
    adb shell dumpsys activity processes "$PKG" | grep -m3 -E 'curProcState=|curCapability=|isFrozen=' | sed 's/^ */  /' || true
    echo "== AudioHardening entries for $PKG"
    adb shell dumpsys audio | grep "AudioHardening.*$PKG" | tail -5 || echo "  none"
    echo "== CSD"
    adb shell cmd audio get-sound-dose-value
    adb shell dumpsys audio | grep -E 'mCurrentCsd|CSD lowering volume to RS1' | tail -4 || true
    echo "== volume"
    adb shell cmd media_session volume --stream 3 --get | grep -i volume || true
    ;;
  simulate)
    ORDER="${1:-drop-first}"; GAP="${2:-300}"; FROM="${3:-}"; KIND="${4:-1x}"
    if [[ -n "$FROM" ]]; then sim --es op simulate --es order "$ORDER" --ei gap "$GAP" --ei from "$FROM" --es kind "$KIND"
    else sim --es op simulate --es order "$ORDER" --ei gap "$GAP" --es kind "$KIND"; fi
    ;;
  set) sim --es op set --ei value "${1:?level}" ;;
  speaker) sim --es op speaker --es on "$( [[ "${1:-on}" == off ]] && echo false || echo true )" ;;
  enable) sim --es op enable --es on "$( [[ "${1:-on}" == off ]] && echo false || echo true )" ;;
  user) adb shell cmd media_session volume --stream 3 --set "${1:?level}" ;;
  vol) adb shell cmd media_session volume --stream 3 --get ;;
  log) adb logcat -d -s VolumeKeeper ;;
  *) sed -n '2,22p' "$0"; exit 1 ;;
esac
