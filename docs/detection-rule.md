# The detection and restore rule

Implemented in `app/modules/volume-keeper/android/src/main/java/com/philkolar/volumekeeper/core/`
(`DecisionEngine.kt`, `CsdNotificationMatcher.kt`, `VolumeTracker.kt`, `Rs1Table.kt`,
`Windows.kt`) and pinned by the tests in `app/modules/volume-keeper/core-tests/` — the test names
start with the rule ids below. **Change the rule only together with its tests and this file.**

## Notation

- Times are `elapsedRealtime` ms. A notification's `postTime` (wall clock) is converted with the
  wall→elapsed offset at receipt.
- `RS1(type)`: A2DP, BLE headset/broadcast, USB headset/device = **6**; wired headset/headphones = **10**.
- `Dev(now)`: the current media output as a device key (`bt:<last 2 address bytes>`, `ble:…`,
  `usb:…`, `wired:<type>`), or none for speaker / earpiece / HDMI / hearing aids.
- Windows (`Windows.kt`):

| Name | ms | Meaning |
|---|---|---|
| `W_BEFORE` | 5000 | a drop may precede the notification's postTime by this much |
| `W_AFTER` | 2000 | after postTime, wait this long for the drop |
| `SETTLE` | 1500 | an output change this close to the drop makes it ambiguous |
| `STALE` | 10000 | a notification received this long after posting is old news |
| `VERIFY` | 300 | read the volume back this long after a restore |
| `ECHO` | 3000 | the 5× second lowering window |
| `SELF` | 1000 | a broadcast landing on our own target this soon is ours |
| `DEVICE_READ_DELAY` | 500 | read a new output's level this long after it appears |
| breaker | 3 / 10 min / 30 min | more than 3 restores of one device in 10 min pauses it 30 min |

## Matcher (M)

A notification is a **CSD notice** iff `pkg == com.android.systemui` AND (`id == 1007` OR
(`channel == "ALR"` AND title matches `/volume lowered/i`)). 1× / 5× is read from the text. Any other
SystemUI notification mentioning volume / hearing / headphones / sound dose is logged as a
**near miss** (no action) so a format change shows up. Dev variant only: the app's own notification
with tag `vk-sim-csd` also matches (simulation).

## Tracker (T)

- **T1** A `VOLUME_CHANGED` broadcast for STREAM_MUSIC records a change `(t, prev, new, Dev(now))`
  and sets the device's tracked level. A change that lands on our pending restore target within
  `SELF` is tagged SELF: it updates the level but is not a "change" for the rule.
- **T2** An output change records a device-change time; `DEVICE_READ_DELAY` later the new output's
  level is read (Bluetooth applies a headset's remembered volume as it connects).
- **T3** Runtime start reads the current output's level. Levels exist only for this process lifetime —
  persisted levels are for display and never become restore targets.
- **T4** The `Settings.System volume_music_*` observer re-reads the volume; if it differs from the
  tracked level it counts as a change (backup for a missed broadcast).

## Rule (R) — on a CSD notice N

- **R0** Skip when the master switch is off, N is stale (`recv − post > STALE`), N is a duplicate
  (same key + postTime), or (at decision time) there is no headphone output / it is excluded.
- **R1** Wait for a qualifying drop, at most until `post + W_AFTER`.
- **R2** D = the **most recent** non-self volume change. It qualifies iff `D.new == RS1(D.type)`,
  `D.prev > D.new`, and `post − W_BEFORE ≤ D.t ≤ post + W_AFTER`. (If the most recent change is
  anything else, keep waiting; at the deadline go to R5.)
- **R3** With a qualifying D, skip if: the output changed in `[D.t − SETTLE, now]` or differs from
  D's (`device_changed`); the current volume ≠ D.new (`volume_changed_after_drop` — **Phil wins**);
  D's device is excluded; D.prev > max; the breaker is tripped.
- **R4** Otherwise restore **D.prev** (signal `notif+broadcast`, or `notif+settings` via T4).
- **R5** No qualifying D by the deadline → fallback only if the current volume == RS1, the tracked
  level for this device is > RS1, and no output change happened after it was observed → restore the
  tracked level (signal `notif+tracker`). Else skip: `volume_changed_after_drop` (a drop was seen
  but something changed after it), `below_rs1`, `no_drop_seen`, `unknown_pre_drop`, `device_changed`.
- **R6** Restore = `setStreamVolume(STREAM_MUSIC, target, 0)`, read back after `VERIFY`: equal →
  RESTORED; still at the lowered level → FAILED `not_applied` (AudioHardening?) + failure
  notification; anything else → RESTORED `changed_again_before_verify`.
- **R7** Echo (5×): within `ECHO` after a restore, a non-self drop on the same device from exactly
  the restored level to RS1, with no newer notice → restore again **once** (signal `echo`). Any other
  change on that device ends the watch.
- **R8** Circuit breaker: more than 3 restores (echo excluded) of one device in 10 min → that device
  is paused for 30 min (`circuit_breaker`).

## Invariants (`InvariantPropertyTest`, 3000 random sequences)

- **I1** A restore target is always a level previously observed for that device (D.prev or a tracked
  level). No code path produces a constant or the maximum.
- **I2** `RS1 < target ≤ max`.
- **I3** No restore without a matched CSD notice before it.
- **I4** A restore only ever raises from RS1 (the volume is at RS1 when it is issued).

## Accepted false positives

- **FP1** SystemUI posts the notice even when the volume was already ≤ RS1; if Phil set exactly RS1
  himself inside the window, his previous level comes back (never above anything he chose).
- **FP2** Phil taps the notification's "Undo" at the same moment: same target, harmless.

## Known false negatives

- **FN1** Notification id *and* title/channel both change in an update → near-miss events show it.
- **FN2** The `ALR` channel is blocked → nothing is posted.
- **FN3** Listener unbound or frozen → Status checklist, keep-alive service.
- **FN4** Lowering during an output switch → skipped by design.
- **FN5** Broadcast missed and level unknown (process restarted after the last change) → `unknown_pre_drop`.
- **FN6** RS1 index changes in an update → drops no longer qualify; update `Rs1Table.kt`.
- **FN7** AudioHardening ignores the restore → FAILED + notification → turn on keep-alive.
