# VolumeKeeper 🎧

Undoes Android's automatic headphone volume lowering ("Calculated Sound Dose", CSD) on Phil's
Pixel 10 — per headset, back to exactly the level it was at before Android dropped it, never
higher, and never touching changes Phil makes himself. Sideloaded, local-only, single user.

## How it works (one paragraph)

When the weekly sound dose crosses a threshold, Android 17 lowers media volume to "RS1" (6/25 on
Bluetooth) and SystemUI posts "Volume lowered to safer level" (notification 1007). VolumeKeeper is a
notification listener: it pairs that notification with the volume broadcast that dropped to RS1
(which carries the previous level) and puts the previous level back within a few hundred ms. A drop
without the notification is Phil's own and is left alone. Details: [CLAUDE.md](./CLAUDE.md),
[docs/csd-findings.md](docs/csd-findings.md), [docs/detection-rule.md](docs/detection-rule.md).

## Building

APKs are built **only on GitHub Actions** (`expo prebuild` + Gradle on the runner; no EAS, no local
builds):

```bash
gh workflow run build-android.yml --ref develop -f variant=dev   # or variant=release
./scripts/vk.sh fetch && ./scripts/vk.sh install .apks/<run>/<name>/<file>.apk
```

## Checks

```bash
cd app && nvm use 22 && npm install --legacy-peer-deps
./scripts/smoke-test.sh            # typecheck → TS tests → Kotlin core tests → lints → device probe
SKIP_DEVICE=1 ./scripts/smoke-test.sh
```

## Layout

```
app/                         Expo SDK 54 app (expo-router, TypeScript strict) — UI only
app/modules/volume-keeper/   Kotlin: notification listener, runtime, pure decision core (+ core-tests/ on the JVM)
scripts/                     smoke-test.sh, bump-version.sh, vk.sh (adb helpers)
docs/                        CSD findings, the detection rule, device checklist
.github/                     ci.yml (TS + Kotlin tests), build-android.yml (signed APK)
```
