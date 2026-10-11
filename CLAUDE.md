# VolumeKeeper — Claude Code Context

This file gives Claude Code full context to work on VolumeKeeper independently.
**Update it in the same commit as any feature, infra, or convention change.**

## Git workflow — never wait to be asked

- Every finished task ends with a **commit AND a push**. Do not stop with local
  commits or a dirty tree and wait for Phil to ask — unpushed work is unfinished work.
- Code lands on `develop`. After pushing `develop`, fast-forward `master` to it and
  push that too: `master` is the GitHub default branch and must always mirror the latest
  `develop`. Every push of `master` builds the release APK on CI.
- Commit messages: a short imperative summary, with the app version in parentheses when
  the commit ships a version bump (e.g. `Echo restore for the 5× double lowering (0.2.1)`),
  plus the `Co-Authored-By:` trailer used by recent commits.

---

## What is VolumeKeeper?

A personal, sideloaded Android app (Pixel 10, Android 17) that undoes Android's automatic
headphone volume lowering — "Calculated Sound Dose" (CSD), MCC-enforced in Australia with no user
toggle. Phil's headphones include quiet ones (Shokz bone-conduction, barely audible at 100%) that
become inaudible after Android drops them to "RS1" (6/25 on Bluetooth), and loud ones that would
hurt at 100%. VolumeKeeper restores **each headset to exactly the level it was at just before
Android lowered it** — never higher, never a blind 100%, and never reverting a change Phil made
himself. Single user, local only: no backend, auth, sync or Play Store.

It replaces a MacroDroid macro ("notification from System UI containing 'volume lowered' → media
volume 100%"). **That macro must stay off while VolumeKeeper's master switch is on** — both reacting
to the same notification race, and MacroDroid's 100% can land last. As of 2026-10-12 Phil has it off.

**Current state (2026-10-12):** v0.1.1 (release variant) is installed on the Pixel with its master
switch on and every setup item granted (listener, Nearby devices, notifications, battery unrestricted —
granted over adb), "Tell me when it restores" turned on for the real-world trial, waiting for a real
lowering; the dev variant (0.1.0) is installed with its master
switch off. Spikes S1, S3 and S4 passed on the phone (docs/csd-findings.md § 9); S5 — forcing a real
lowering — could not be completed because the WH-1000XM5 does not accumulate sound dose on this
build, so the first real lowering is the S5 test. Repo is public (made so 2026-10-12 at Phil's request).

---

## How Android lowers the volume (details: [docs/csd-findings.md](docs/csd-findings.md))

- Two system paths, both ending in `SoundDoseHelper.onLowerVolumeToRs1()`: the **1× dose warning**
  (SystemUI dialog; lowered if not acknowledged within 5 s) and the **5× dose** (lowered at once by
  SoundDoseHelper, and SystemUI calls lowerVolumeToRs1 a second time). Momentary-exposure warnings
  never lower on their own in A17; the dialog's "Lower volume" button lowers **without** a
  notification (Phil's choice — never undone).
- The drop: `setStreamVolume(MUSIC, safeIndex/10, 0)` → index **6** on BT/LE/USB, **10** wired (of 25).
  It produces a normal `VOLUME_CHANGED_ACTION` broadcast carrying the previous level.
- The notification: `com.android.systemui`, **id 1007**, channel **`ALR`**, title **"Volume lowered to
  safer level"**, text "…high for longer than recommended" (1×) / "…exceeded the safe limit for this
  week" (5×). Arrives within a few hundred ms of the drop, in either order.
- **AudioHardening (Android 17, flags on for this phone)** silently ignores `setStreamVolume` from apps
  in the wrong process state. A notification listener's binding gives BOUND_FOREGROUND_SERVICE + the
  audio-control capability, which passes; an FGS passes too. **targetSdk stays 36** (37 tightens it).
- Notification listeners are bound with `BIND_SIMULATE_ALLOW_FREEZE` only; if Google enables real
  freezing of idle listeners, turn on the keep-alive service.
- Caller identity is not available to an app (READ_LOGS needs per-minute consent; DUMP optional).

## The detection and restore rule (details: [docs/detection-rule.md](docs/detection-rule.md))

Restore only when SystemUI's CSD notification is paired with the most recent volume change being a
drop to exactly RS1 (within 5 s before / 2 s after the notification), on the same output, with the
volume still at RS1 and no output change around it. Target = the broadcast's previous level, or —
only if that broadcast was missed — the level tracked for that device in this process lifetime.
Invariants, pinned by `core-tests/` (incl. a 3000-sequence randomised test):

| Id | Invariant |
|---|---|
| I1 | A restore target is always a level previously observed for that device. No constant, no max. |
| I2 | RS1 < target ≤ max |
| I3 | No restore without a matched CSD notification |
| I4 | A restore only raises from RS1; any change after the drop means Phil wins |

The 5× double lowering is re-restored once ("echo"); more than 3 restores of a device in 10 min
pauses it for 30 min. All timing constants live in `core/Windows.kt` and nowhere else.
**Change the rule only together with its tests and docs/detection-rule.md.**

---

## Stack

| Layer | Tech |
|-------|------|
| App UI | React Native 0.81 + Expo SDK 54, expo-router 6, TypeScript strict (same toolchain as PKTrainer) |
| Background logic | Kotlin, Expo local module `app/modules/volume-keeper` (NotificationListenerService host) |
| Decision core | Pure Kotlin `core/` package — no Android imports — tested on the JVM (`core-tests/`, Gradle 8.14.3, Kotlin 2.1.20) |
| Storage | Native SQLite (`SQLiteOpenHelper`: `devices`, `events`) + SharedPreferences (settings). JS reads through the module. |
| Build | **GitHub Actions only**: `expo prebuild` + `./gradlew assembleRelease` (arm64) on the runner, release-keystore signed. No EAS. |
| Tests | Kotlin: 36 JVM tests (matcher, engine scenarios, invariant property test). TS: node:test via tsx (formatting helpers). |

---

## Project Structure

```
~/repos/VolumeKeeper/
├── CLAUDE.md, README.md, .nvmrc (22), .gitignore
├── .github/workflows/
│   ├── ci.yml                    # push/PR develop+master: "app" (smoke-test phases 1,2,4,5) + "kotlin-core" (gradle test)
│   └── build-android.yml         # dispatch (variant=release|dev) + push master: prebuild + gradle, release-signed APK artifact
├── scripts/
│   ├── smoke-test.sh             # 1 typecheck · 2 TS tests · 3 Kotlin core tests · 4 stray-hex lint · 5 core-purity lint · 6 device probe
│   ├── bump-version.sh           # patch|minor|major: app.json version + app.config.ts versionCode together
│   └── vk.sh                     # adb helpers: fetch/install CI APKs, grant, state, simulate, set, speaker, enable, user, vol, log
├── docs/
│   ├── csd-findings.md           # AOSP A17 call paths, notification fields, hardening, listener binding, phone facts, measurements
│   ├── detection-rule.md         # the rule (R0–R8), windows, invariants, FP/FN — mirrors core-tests names
│   └── device-checklist.md       # numbered on-device checks per phase
└── app/
    ├── app.json                  # name, slug, dark, android.package, permissions, blockedPermissions, adaptive icon, plugins
    ├── app.config.ts             # APP_VARIANT=dev → .dev package/name; versionCode; build-properties (minSdk 34, targetSdk 36 — see pitfalls); plugins
    ├── package.json              # main expo-router/entry; scripts typecheck, test, prebuild
    ├── plugins/withReleaseSigning.js  # release signingConfig from VK_KEYSTORE_* env (CI); debug signing otherwise
    ├── plugins/withDevTools.js        # dev variant only: <meta-data vk.simulation> + SimulationReceiver (permission DUMP)
    ├── scripts/make-icons.py     # regenerates assets/*.png (speaker + rising arrow)
    ├── assets/                   # icon, adaptive-icon(+mono), splash-icon, favicon
    ├── app/
    │   ├── _layout.tsx           # Stack, light status bar, crash-diagnostics Alert
    │   ├── (tabs)/_layout.tsx    # Status / Devices / Log, PKTrainer pill tab icons, haptics, back exits
    │   ├── (tabs)/index.tsx      # Status: health, master switch, now-playing level bar (RS1 tick), setup checklist + fixes, last lowering
    │   ├── (tabs)/devices.tsx    # per output: last level, restores, "Restore this device" (exclude), forget
    │   ├── (tabs)/log.tsx        # events (Lowerings / Everything), tap a row for the raw notification fields
    │   ├── settings.tsx          # keep-alive, notify on restore/failure, dismiss Android's notice; dev: speaker-as-headphones, simulate
    │   └── debug.tsx             # dev: live runtime trace
    ├── components/               # ui.tsx + Motion.tsx (from PKTrainer), LevelBar, EventRow, ToggleRow
    ├── hooks/                    # useVolumeKeeper.ts (useNative: refetch on onChange / focus / foreground; useNow), useBackHandler
    ├── lib/                      # theme.ts (only hex file), motion.ts, format.ts (PURE: event text, checklist, health), __tests__/
    └── modules/volume-keeper/
        ├── expo-module.config.json, index.ts (typed bridge), src/VolumeKeeper.types.ts
        ├── android/build.gradle, android/src/main/AndroidManifest.xml (listener, keep-alive FGS, boot receiver, permissions)
        ├── android/src/main/res/drawable/ic_vk_notification.xml
        ├── android/src/main/java/com/philkolar/volumekeeper/
        │   ├── core/      Model, Windows, Rs1Table, CsdNotificationMatcher, VolumeTracker, DecisionEngine   (PURE)
        │   ├── runtime/   VkRuntime (process singleton, "vk-engine" thread), AudioObserver, DeviceResolver, Store, Prefs, Notifier
        │   ├── service/   VkListenerService (host), KeepAliveService (optional specialUse FGS), BootReceiver
        │   ├── debug/     SimulationReceiver (dev variant manifest only)
        │   └── VolumeKeeperModule.kt (JS bridge)
        └── core-tests/    standalone JVM Gradle project compiling ../android/.../core + src/test/kotlin
```

---

## Delivery status

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Repo (private, develop/master), Expo app, native module, CI, GitHub-only APK build, keystore + secrets, docs | **done** 2026-10-12 (v0.1.0 pushed; CI + builds dispatched) |
| 1 | Spikes S1–S6 on the phone (device-checklist items 3–7) | S1, S3, S4 **passed** 2026-10-12 (background + screen-off restores accepted, procstate 5 + capability A, ~0.3 s notice delivery). S5 waits for a real lowering (the Sony does not accumulate dose). S2 partly (volume + output traces seen). S6 not run. |
| 2 | MVP acceptance (items 8–12), release variant installed, one-week soak | 0.1.1 release installed 2026-10-12, master on; real-world soak started |
| 3 | Polish (items 13–15): keep-alive UX, notifications, CSV share, health notification | partly in 0.1.0 (toggles exist) |

---

## Development Environment

### Prerequisites (on Phil's PC)
- Node **22** via nvm (`.nvmrc`). `gh` authenticated as PhilipKolar (repo + workflow scopes).
- JDK `~/.jdks/temurin-21` — only for the JVM core tests (`core-tests/`).
- The Pixel connects over **wireless debugging** (the USB cable carries no data).

### First-time setup
```bash
git clone https://github.com/PhilipKolar/VolumeKeeper.git ~/repos/VolumeKeeper
cd ~/repos/VolumeKeeper/app && nvm use 22 && npm install --legacy-peer-deps
```

### Connecting the Pixel (wireless debugging)
1. Phone: Developer options → Wireless debugging → "Pair device with pairing code"; keep the dialog open.
2. `adb pair <ip:pairing-port> <code>` (only works while the dialog is open).
3. `adb mdns services` shows the `_adb-tls-connect` port → `adb connect <phone-ip>:<port>`.
   The connect port changes whenever wireless debugging restarts. Keep one adb transport.

### Building — GitHub only (Phil's rule)
**Every APK is built by `.github/workflows/build-android.yml` on GitHub Actions.** Never EAS (neither
cloud nor `eas build --local`), and never a local Gradle/`expo run:android` build on the PC. Local
`expo prebuild` (code generation, to inspect the generated `android/`) and the JVM core tests are fine.
```bash
gh workflow run build-android.yml --ref develop -f variant=dev       # VolumeKeeper Dev (simulation tools)
gh workflow run build-android.yml --ref develop -f variant=release   # the real app (also built on every master push)
gh run list --workflow=build-android.yml --limit 3
./scripts/vk.sh fetch [run-id]                                       # → .apks/<run>/<artifact>/<name>.apk
./scripts/vk.sh install .apks/<run>/<artifact>/<name>.apk            # force-stop, install, print versionName
```
A build takes ~10–15 min. Both variants are signed with the release keystore, so updates install
over each other in place; the two variants are separate apps with separate data.

### Deploy to the Pixel and verify it yourself (the standing loop)
Phil's standing instruction after a change is "deploy and test on my phone" — run the whole loop
without waiting for him:

1. Checks first: `./scripts/smoke-test.sh` (or `SKIP_DEVICE=1`), bump the version
   (`./scripts/bump-version.sh`), commit, push `develop` (and fast-forward `master`).
2. Build on GitHub (above), wait for the run (`gh run watch <id> --exit-status`), `vk.sh fetch`,
   `vk.sh install …`, confirm the versionName.
3. **Before enabling any master switch, confirm MacroDroid's "volume lowered" macro is off** (it is as
   of 2026-10-12; ask if unsure). Never have both VolumeKeeper variants' master switches on.
4. Drive the app like Phil would — golden path first, then edge cases. `adb shell monkey -p <pkg> 1` starts it.
   - `adb exec-out screencap -p > /tmp/shot.png` and Read the image to see where you are.
   - **Screenshots render at 891×2000 but the device is 1080×2424 — multiply every coordinate read off
     a screenshot by 1.21 before `adb shell input tap X Y`.**
   - No-vision fallback: `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml`
     (device-pixel bounds, no scaling).
   - Take a screenshot after each tap; never fire a blind sequence of taps.
   - Crashes: `adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime"`; runtime lines: `vk.sh log`.
5. Exercise the rule with `scripts/vk.sh` (dev variant): `vk.sh speaker on` lets simulations run on the
   phone speaker; `vk.sh simulate drop-first 300 18`, `vk.sh simulate notif-first 300 18`,
   `vk.sh user 6` (must not restore); check the Log tab and `vk.sh state`.
6. If the phone is locked, do NOT try to bypass the lockscreen — say "phone unlocked please" and carry
   on with PC-side work. Bluetooth checks need a headset: say "headset connected please".
7. Leave the phone as you found it: set the volume back, `vk.sh speaker off`, dev master switch off
   unless Phil wants it on.
8. Wrap up: commit on `develop`, push, fast-forward `master`, push that too — every time.

### Testing a lowering on demand
- **Simulation (dev variant):** `SimulationReceiver` (adb-only, permission DUMP) lowers STREAM_MUSIC to
  the current output's RS1 itself and posts a look-alike notification (tag `vk-sim-csd`), in either
  order with a chosen gap. Exercises everything except the real SystemUI notification.
- **Scripted user changes:** `vk.sh user N` (`cmd audio set-volume 3 N`; `cmd media_session volume --set` is logged but silently not applied on Android 17).
- **Background restore test (S3):** `vk.sh set N` → a Log "test" row says Applied / Not applied.
- **Real CSD (approved by Phil 2026-10-12 for the spikes):** record `adb shell cmd audio
  get-sound-dose-value` (3.3645012 on 2026-10-12), play music on headphones, `adb shell cmd audio
  set-sound-dose-value 3.99` (1× dialog → wait 5 s) or `4.99` (5×, immediate), then
  `set-sound-dose-value <recorded>`. This collapses the dose history to one record (accepted).
- **Never, without asking Phil:** any other `audio_safe_csd_*` / global / secure setting change,
  `reset-sound-dose-timeout`, touching MacroDroid. Pre-approved for spikes: scripted volume changes (`cmd audio set-volume 3 N`),
  `cmd notification allow_listener` (dev build), `dumpsys deviceidle force-idle` (always `unforce`
  after), `pm grant com.philkolar.volumekeeper.dev android.permission.DUMP`.

### Checks (run before committing; CI runs all but the device probe)
```bash
./scripts/smoke-test.sh                  # 1 tsc · 2 TS tests · 3 Kotlin core tests · 4 hex lint · 5 core purity · 6 device probe
SKIP_DEVICE=1 ./scripts/smoke-test.sh
cd app/modules/volume-keeper/core-tests && JAVA_HOME=~/.jdks/temurin-21 ./gradlew test   # core tests alone
```

---

## Secrets

All secrets are gitignored or outside the repo. Never commit `*.keystore`, `.env`, `credentials.json`.

### GitHub Actions secrets (repo `PhilipKolar/VolumeKeeper`)
| Secret | Purpose | State |
|--------|---------|-------|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0` of the release keystore | set 2026-10-12 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password | set |
| `ANDROID_KEY_PASSWORD` | key password (same as keystore) | set |

The key alias `volumekeeper` is plain text in build-android.yml, not a secret: GitHub masks secret
values in logs, and an alias secret turned every "volumekeeper" in the build log into `***`.
No `EXPO_TOKEN` and no EAS project: the build never talks to Expo's servers.

The release keystore lives at `~/VolumeKeeper-secrets/volumekeeper-release.keystore` (alias
`volumekeeper`; password in `~/VolumeKeeper-secrets/README.txt` until Phil moves both to the password
manager). SHA-256 `A2:6E:36:C9:…:70:91:A1`. Losing it means future CI APKs cannot update the installed app.

---

## Building the APK (CI)

### Claude Code: never build locally, never use EAS — dispatch the workflow
See "Building — GitHub only" above. The workflow fails if the APK comes out debug-signed.

### Versioning
`./scripts/bump-version.sh patch|minor|major` bumps `app/app.json` version and `app/app.config.ts`
versionCode together (CI's clean prebuild bakes them in). patch = fixes/tweaks, minor = features,
major = redesign. Current: **0.1.1 / versionCode 2** (release installed on the Pixel 2026-10-12).

### Git workflow (summary — full rule at the top of this file)
- Every finished task ends with a commit AND a push on `develop`, then a fast-forward of `master`
  and a push of that too. Never commit directly to `master`.
- Every push of `master` builds the release APK.

---

## Design conventions

### Visual
- Dark only. Tokens in `lib/theme.ts` (PKTrainer's palette: bg `#0a0a0a`, card `#111`, accent blue
  `#5b8def`, success green `#22e39a`, warning `#eab308`, danger `#ef4444`). No hex anywhere else in
  TS/TSX (smoke test enforces; mark unavoidable brand colours `// brand-color`).
- Tab bar: pill active indicator, no labels, Ionicons (pulse / headset / list).
- Event rows: green check = restored, red = failed, grey = left alone (with the reason first), amber =
  near miss. Levels read `18/25`; the level bar's yellow tick is RS1.

### Code
- **The decision logic is Kotlin and only Kotlin**, in the pure `core/` package (no `android.*`,
  `androidx.*`, `expo.*` imports — smoke test phase 5). It runs identically on the JVM in
  `core-tests/`. JS never re-implements it; the UI only displays what the native side recorded.
- **Native owns all state.** JS reads via the module and refetches on `onChange` / focus / foreground.
- Every engine call happens on the single `vk-engine` HandlerThread (`VkRuntime.handler`); the engine
  takes a `Probe` (current output + index) read right before the call and returns `Action`s.
- **A restore target only ever comes from an observation** (the drop broadcast's previous level, or
  the tracker). Never introduce a constant, a max, or a persisted level as a target.
- All timing constants in `core/Windows.kt`.
- Pure TS helpers (`lib/format.ts`) are unit-tested from Node; screens stay thin.

### Known pitfalls (do not reintroduce)
- **targetSdk is pinned to 36** (`app.config.ts`). Android 17's AudioHardening uses DENIED_IF_FULL for
  targetSdk ≥ 37; raising it (or an Expo SDK upgrade that does) needs spike S3 re-run first.
- **`setStreamVolume` can be silently ignored** (AudioHardening). Every restore is read back; never
  assume it worked.
- `VOLUME_CHANGED_ACTION` is sent only for the stream's *current* device and is deferred for cached
  processes; the Bluetooth stack applies a headset's remembered level *before* it becomes current — so
  after any output change the runtime re-reads the level (`DEVICE_READ_DELAY`).
- The listener must stay in the app's main process (no `android:process`): the JS module talks to the
  same `VkRuntime` singleton.
- `Settings.System volume_music_*` keys are not readable by apps (not `@Readable`): the observer is a
  trigger only; read `getStreamVolume` instead.
- Bluetooth device keys use the last two address bytes, because addresses are anonymised
  (`XX:XX:XX:XX:AB:CD`) until BLUETOOTH_CONNECT is granted — keeps keys stable across the grant.
- MacroDroid's macro + VolumeKeeper = possible 100% blast. Both VolumeKeeper variants enabled = double
  restore. The dev variant ships with its master switch off.
- Android `Alert.alert` shows at most three buttons (PKTrainer lesson) — anything longer needs a sheet.
- `adb install` hangs while the app is running — `vk.sh install` force-stops first.
- **The native module's Gradle namespace is `com.philkolar.volumekeeper.module`, never the app id.**
  With `com.philkolar.volumekeeper` the release app and the library both generated
  `com.philkolar.volumekeeper.BuildConfig` and the release build failed in `mergeDexRelease`
  ("defined multiple times"); the dev variant (`….dev`) hid it. Kotlin packages stay
  `com.philkolar.volumekeeper.*`; only `R` lives under `.module`.

---

## Backlog

- [ ] S5: capture the first real lowering (Log tab → tap the row for the notification fields; check
      `dumpsys audio` for `CSD lowering volume to RS1`), record it in docs/csd-findings.md § 9 and tune
      `Windows.kt` if the drop/notice gap differs from the simulation. Find out why the WH-1000XM5 does
      not accumulate dose (absolute volume? offload path?) — only matters for testing.
- [ ] S6 (DUMP evidence) and the 20× S3 run with `dumpsys deviceidle force-idle`.
- [ ] Decide the keep-alive default from S3 (currently off).
- [ ] Phase 2 acceptance (items 8–12) on the release variant, then a one-week soak; Phil deletes the
      MacroDroid macro afterwards.
- [ ] CSV share of the event log.
- [ ] Health notification when the listener stays disconnected while access is granted.
- [ ] Optional DUMP "evidence snapshot" of `dumpsys audio` per event (only if S6 shows it works).
- [ ] Move the keystore + password from `~/VolumeKeeper-secrets/` to the password manager.
