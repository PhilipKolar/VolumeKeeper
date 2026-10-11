# On-device checklist

Run on the Pixel before calling a phase done (and S3/item 7 after every Android update). Before any
item with a master switch on: **MacroDroid's "volume lowered" macro is off.** Commands are
`scripts/vk.sh …` (dev variant unless `VK_PKG=com.philkolar.volumekeeper`).

## Phase 0 — install
1. CI APK installs over adb; `vk.sh state` shows the expected versionName.
2. App opens to a dark Status tab; checklist shows the missing permissions with working Fix buttons
   (notification access opens the listener detail page; battery opens the system dialog).

## Phase 1 — spikes
3. **S1 listener lifecycle** — after granting access: `vk.sh state` shows listener bound,
   `curProcState=5` (BOUND_FOREGROUND_SERVICE), capability contains `A`, `isFrozen=false`; same after
   swiping the app away, after 15 min screen-off, after a reboot (within 60 s of unlock), after an
   app update.
4. **S2 signals** — Debug → Live trace shows every `vol a→b` for hardware keys, the volume panel and
   `vk.sh user N`, with correct before/after; `output →` lines on headset connect/switch; a `read`
   line ~0.5 s after connect with BT's remembered level.
5. **S3 background restore** — `vk.sh set N` 20× across: app open, app swiped away, screen off ≥ 10 min
   (also with `dumpsys deviceidle force-idle`, then `unforce`): every Log "test" row says Applied;
   `vk.sh state` lists no AudioHardening entries for the package. Any failure → keep-alive default on.
6. **S4 synthetic CSD** — with "treat speaker as headphones" (or a headset): `vk.sh simulate
   drop-first 300 18` and `notif-first 300 18` restore to 18 within ~1 s (Log shows latency);
   `drop-first 6000 18` and `notif-first 3000 18` are left alone; `vk.sh simulate drop-only 0 18`
   (or `vk.sh user 6`) is never restored.
7. **S5 real CSD** — record `cmd audio get-sound-dose-value`; music on headphones;
   `cmd audio set-sound-dose-value 3.99` → dialog → wait it out → lowered → restored (Log: 1× dose,
   notif+broadcast); then `4.99` → immediate lowering → restored (5× dose, possibly an `echo` row);
   then `set-sound-dose-value <recorded>`. Notification detail (tap the Log row) records id, channel,
   title, text, actions. `vk.sh state` shows matching `CSD lowering volume to RS1` lines.

## Phase 2 — MVP
8. Two headsets in turn (e.g. Sony at 12, Shokz at 25): a lowering on each comes back to its own level.
9. Keys, volume panel, Spotify's slider and headset buttons are never reverted (Log shows nothing).
10. App swiped away, screen off, after reboot: a simulated lowering still restores.
11. Speaker: nothing happens. A device with "Restore this device" off: Log says excluded. Master off:
    Log says master switch was off.
12. The first natural lowering on the release app is restored within 1 s.

## Phase 3 — polish
13. Keep-alive on → silent ongoing notification, `curProcState=4`; off → gone.
14. "Tell me when it restores" posts a quiet notification; "Dismiss Android's notice" clears
    SystemUI's notification after a real restore.
15. A failed restore (simulate with AudioHardening blocked, or note any real one) posts the failure
    notification.
