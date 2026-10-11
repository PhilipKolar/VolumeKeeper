// Expo config plugin, dev variant only (APP_VARIANT=dev, see app.config.ts): turns on the
// simulation path. Adds <meta-data vk.simulation=true> (read by runtime/Prefs.kt) and the
// SimulationReceiver, protected by android.permission.DUMP so only adb/shell can send to it
// (scripts/vk.sh). The release variant never contains either.
const { withAndroidManifest } = require("@expo/config-plugins")

const RECEIVER = "com.philkolar.volumekeeper.debug.SimulationReceiver"

module.exports = function withDevTools(config) {
  return withAndroidManifest(config, (config) => {
    const app = config.modResults.manifest.application && config.modResults.manifest.application[0]
    if (!app) return config
    app["meta-data"] = (app["meta-data"] || []).filter((m) => m.$["android:name"] !== "vk.simulation")
    app["meta-data"].push({ $: { "android:name": "vk.simulation", "android:value": "true" } })
    app.receiver = (app.receiver || []).filter((r) => r.$["android:name"] !== RECEIVER)
    app.receiver.push({
      $: {
        "android:name": RECEIVER,
        "android:exported": "true",
        "android:permission": "android.permission.DUMP",
      },
    })
    return config
  })
}
