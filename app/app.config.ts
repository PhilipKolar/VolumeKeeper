import { ExpoConfig, ConfigContext } from "expo/config"

// Dynamic layer on top of app.json. app.json owns the semver `version`;
// this file owns the APK `versionCode`. Bump BOTH via scripts/bump-version.sh.
// APP_VARIANT=dev builds com.philkolar.volumekeeper.dev / "VolumeKeeper Dev": a separate app with
// the simulation receiver, its master switch off by default (never enable both variants at once).
const IS_DEV_VARIANT = process.env.APP_VARIANT === "dev"
const BASE_ID = "com.philkolar.volumekeeper"
const APP_ID = IS_DEV_VARIANT ? `${BASE_ID}.dev` : BASE_ID

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: IS_DEV_VARIANT ? "VolumeKeeper Dev" : "VolumeKeeper",
  slug: "volumekeeper",
  plugins: [
    ...(config.plugins ?? []),
    // targetSdk stays 36 ON PURPOSE. Android 17's AudioHardening blocks background
    // setStreamVolume harder for apps targeting 37+ (HardeningEnforcer: DENIED_IF_FULL instead of
    // DENIED_IF_PARTIAL). Raising it needs the spike-S3 background-restore test re-run first.
    // minSdk 34: Pixel-only app; lets the Kotlin use API 33/34 calls without guards.
    ["expo-build-properties", { android: { minSdkVersion: 34, compileSdkVersion: 36, targetSdkVersion: 36 } }],
    "./plugins/withReleaseSigning",
    ...(IS_DEV_VARIANT ? ["./plugins/withDevTools"] : []),
  ],
  android: {
    ...config.android,
    package: APP_ID,
    versionCode: 2,
  },
})
