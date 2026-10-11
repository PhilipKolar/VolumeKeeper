# How Android 17 lowers headphone volume (CSD) — findings

Read from AOSP `frameworks/base` branch **`android17-release`** (compared with `main`) and the
Pixel 10 (`frankel`, Android 17, `CP3A.260905.009`) over read-only adb on 2026-10-12. File names
below are relative to `frameworks/base`. Measured timings from the spikes are appended at the end.

## 1. The two system-initiated lowering paths

Both end in `services/core/java/com/android/server/audio/SoundDoseHelper.java`
→ `onLowerVolumeToRs1()`:

| Trigger | Sequence | SystemUI notification |
|---|---|---|
| **1× dose warning** — dose crosses 1.0, 2.0, 3.0, 4.0, 6.0, 7.0… | `onNewCsdValue` → `postDisplayCsdWarning(CSD_WARNING_DOSE_REACHED_1X)` → SystemUI `packages/SystemUI/src/com/android/systemui/volume/CsdWarningDialogDelegate.java` shows "Keep listening / Lower volume". If not acknowledged within **`NO_ACTION_TIMEOUT_MS = 5000`** (SystemUI's own constant; the 7000 ms in SoundDoseHelper is only the display duration it passes along): `mCachedMediaStreamVolume = getStreamVolume(MUSIC)` → `lowerVolumeToRs1()` (binder call that only *posts* `MSG_LOWER_VOLUME_TO_RS1`) → `sendNotification(false)` | yes |
| **5× dose** — crossing exactly 5.0 | SoundDoseHelper calls `postLowerVolumeToRs1()` itself (immediate) **and** posts `DOSE_REPEATED_5X`; SystemUI's `show5XNotification()` caches the volume, calls `lowerVolumeToRs1()` a second time (normally a no-op) and sends the notification | yes |
| Momentary exposure (≤ once / 20 h) | dialog only — **no timeout action in A17** (`mNoUserActionRunnable` is null for it) | no |
| Phil taps "Lower volume" in the dialog | `lowerVolumeToRs1()`, **no notification** — his choice, never undone | no |

Dose bookkeeping: `onNewCsdValue` raises `mNextCsdWarning` by 1 after each warning and lowers it by
1 when the dose falls a whole step below it, so a heavy listener sitting around 3–5× in the 7-day
rolling window re-crosses the same thresholds again and again — hence "it keeps happening".

`onLowerVolumeToRs1()`:
- device = first `USAGE_MEDIA` device (`getDevicesForAttributesInt(..., forVolume=true)`),
- `index = safeMediaVolumeIndex(type) / 10` — this phone: `mSafeMediaVolumeIndex[128]=60` (A2DP),
  `[536870912]=60` (BLE headset), `[67108864]=60` (USB headset), `[4]=[8]=100` (wired) → **6 or 10**
  on the 0–25 STREAM_MUSIC scale,
- lowers only if `index < current`,
- `setStreamVolumeWithAttributionInt(MUSIC, index, flags 0, ada, "android", null, true)`.

Consequences:
- No volume UI (flags 0). A normal **`android.media.VOLUME_CHANGED_ACTION`** broadcast goes out with
  `EXTRA_VOLUME_STREAM_VALUE` and `EXTRA_PREV_VOLUME_STREAM_VALUE` (sent only for the stream's
  current device; `sendBroadcastToAll` clears the calling identity, so the sender is system_server).
  The broadcast is a `<protected-broadcast>` with `DELIVERY_GROUP_POLICY_MOST_RECENT` and
  `DEFERRAL_POLICY_UNTIL_ACTIVE` (deferred for *cached* processes).
- `dumpsys audio` "Volume changes" does **not** log it as `setStreamVolume … from android` (a
  non-null `ada` skips that line); it shows only `sending VOLUME_CHANGED … index:6 (was:N)`, plus
  **`CSD lowering volume to RS1`** in the `## SoundDoseHelper` event log.
- Hardware keys log as `adjustSuggestedStreamVolume … from android/android uid:1000`, so even the
  caller package would not separate them from CSD.

## 2. The notification

`CsdWarningDialogDelegate.sendNotification()`:

| Field | Value |
|---|---|
| package | `com.android.systemui` |
| id / tag | **1007** (`SystemMessage.NOTE_CSD_LOWER_AUDIO`, `proto/src/system_messages.proto`) / none |
| channel | **`ALR`** ("Alerts", importance 4 — confirmed in this phone's `dumpsys notification`) |
| title | **"Volume lowered to safer level"** (`csd_lowered_title`) |
| text 1× | "Headphone volume has been high for longer than recommended" (`csd_system_lowered_text`) |
| text 5× | "Headphone volume has exceeded the safe limit for this week" (`csd_500_system_lowered_text`) |
| other | `CATEGORY_SYSTEM`, auto-cancel, local-only, `BigTextStyle`, small icon `hearing`, content → Sound settings |
| actions | "Undo" only when `Flags.sounddoseCustomization()` and actions are supplied (restores SystemUI's cached volume) |

Timing: posted right after `lowerVolumeToRs1()` returns, while the drop runs on the audio handler,
so drop broadcast and notification arrive within ~0–300 ms of each other **in either order**; on 5×
the drop is first by construction. Strings are localised (`product="default"`); Pixel may overlay.

## 3. After a restore

`setStreamVolume` from an app is accepted while CSD is on: `mSafeMediaVolumeState=DISABLED`, so the
legacy safe-volume dialog/deferral never applies (this is why MacroDroid's 100% action works).
Restoring does not re-warn or re-lower; the next lowering waits for the next dose threshold. Dose
keeps accumulating from actual loudness (`audio_safe_csd_current_value`, `audio_safe_csd_dose_records`
— records are `timestamp,duration,0.010,meanMEL`, i.e. ~1 % dose each).

## 4. AudioHardening — can a background app still set the volume?

`services/core/java/com/android/server/audio/HardeningEnforcer.java` `blockVolumeMethod()` runs for
`setStreamVolume` (api 100), `adjustStreamVolume` etc. **When it blocks, the call silently does
nothing.** This phone has `media_audio/com.android.media.audio.hardening_partial_volume=true` and
`hardening_strict=true` (`device_config list`), and dumpsys shows
`AudioHardening volume control for api 100 would be ignored for com.alarmclock.xtreme.free`.

- Two app ops, both `MODE_FOREGROUND`: `CONTROL_AUDIO_PARTIAL` (allowed while uid state ≤
  FOREGROUND, i.e. procstate ≤ `BOUND_FOREGROUND_SERVICE`) and `CONTROL_AUDIO` (needs the
  `FOREGROUND_AUDIO_CONTROL` process capability).
- Enforcement for a non-privileged caller: exact-alarm permission **or Doze allow-list** → blocked
  only if PARTIAL is denied; else `targetSdk < 37` → blocked only if PARTIAL is denied; else blocked
  if either op is denied.
- A notification listener is bound by system_server with `BIND_FOREGROUND_SERVICE` →
  `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (`psc/OomAdjusterImpl.java`), and audio capability always
  propagates over bindings from the client (`OomAdjuster.getAudioCapabilitiesFromClient`). Any FGS
  also grants it ("Any FGS grants audio control if the flag is enabled" — MacroDroid's process shows
  capability `---NFUATI`). So the listener process passes; an FGS passes too.
- Hence: targetSdk pinned at 36, battery exemption requested, every restore read back.

## 5. Notification-listener lifecycle

`NotificationManagerService.NotificationListeners.getBindFlags()`: `BIND_AUTO_CREATE |
BIND_FOREGROUND_SERVICE | BIND_NOT_PERCEPTIBLE | BIND_ALLOW_WHITELIST_MANAGEMENT |
BIND_SIMULATE_ALLOW_FREEZE`, plus `BIND_ALLOW_FREEZE` only when `Flags.allowFreezingIdleNls()`. On this
build MacroDroid's listener binding shows `CR FGS !PRCP S!CPU` — simulate only, so idle listeners are
not frozen (yet). If Google turns that on, the keep-alive FGS is the fallback.

## 6. Caller identity: dead ends

- `READ_LOGS` (`logcat -b events volume_changed` carries the caller): Android 13+ asks for consent on
  every access and approvals expire after 60 s (`server/logcat/LogcatManagerService.java`) — useless
  in the background.
- `DUMP` via `pm grant` could let the app read `dumpsys audio` (`CSD lowering volume to RS1`); not
  needed for the rule (spike S6, optional evidence only).

## 7. Phone facts (2026-10-12)

- `cmd audio get-sound-dose-value` → 3.3645012; next warning at 4.0 (a 1× dialog-timeout lowering).
- STREAM_MUSIC 0–25; AudioService keeps one index per device *type* (`80 (bt_a2dp): 25`), so every
  A2DP headset shares it. The BT stack remembers a per-headset volume and applies it on connect
  *before* the device is current (`BT connected … WH-1000XM5 … volume=11` →
  `postSetVolumeIndexOnDevice` in `AudioDeviceInventory`).
- `Settings.System volume_music_bt_a2dp` is persisted ~500 ms after a change (`PERSIST_DELAY`) and
  is not readable by apps (not `@Readable`), so it only works as a change trigger.
- Paired headsets (all `AUDIO_DEVICE_CATEGORY_HEADPHONES`): Shokz OpenSwim Pro, Shokz OpenRun Pro 2,
  Mustang Micro Plus, Sony WH-1000XM5, JLab GO Sport+, Pixel Buds Pro 2.
- Sony's app (`com.sony.songpal.mdr`) has its own "Effects on hearing" channel — a separate feature;
  VolumeKeeper ignores anything that is not SystemUI's notification.

## 8. Test hooks (`cmd audio`, `services/core/.../AudioManagerShellCommand.java`)

- `get-sound-dose-value` — read-only.
- `set-sound-dose-value <f>` — `AudioManager.setCsd`: sets the dose, `mNextCsdWarning = floor(f+1)`,
  and **replaces the dose history with one record**. Approved by Phil for spike S5 (3.99 → 1×,
  4.99 → 5×), then set back to the recorded value.
- `reset-sound-dose-timeout` — resets the 20 h momentary-exposure timer (in memory; never lowers).
- `cmd media_session volume --stream 3 --set N` / `cmd audio set-volume 3 N` — scripted user-like changes.

## 9. Measured on the phone (spikes)

_Filled in as the spikes run — see CLAUDE.md § Delivery status._
