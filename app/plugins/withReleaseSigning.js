// Expo config plugin: sign `assembleRelease` with the release keystore when CI provides it.
// .github/workflows/build-android.yml decodes ANDROID_KEYSTORE_BASE64 to a file and exports
// VK_KEYSTORE_PATH / VK_KEYSTORE_PASSWORD / VK_KEY_ALIAS / VK_KEY_PASSWORD. Without them the
// template's debug signing is kept, so a plain prebuild still works. `expo prebuild` regenerates
// android/ from scratch, which is why this lives in a plugin and not in android/app/build.gradle.
const { withAppBuildGradle } = require("@expo/config-plugins")

const RELEASE_CONFIG = `signingConfigs {
        release {
            if (System.getenv('VK_KEYSTORE_PATH')) {
                storeFile file(System.getenv('VK_KEYSTORE_PATH'))
                storePassword System.getenv('VK_KEYSTORE_PASSWORD')
                keyAlias System.getenv('VK_KEY_ALIAS')
                keyPassword System.getenv('VK_KEY_PASSWORD')
            }
        }`

module.exports = function withReleaseSigning(config) {
  return withAppBuildGradle(config, (config) => {
    let src = config.modResults.contents
    if (src.includes("VK_KEYSTORE_PATH")) return config
    // 1. The release buildType: use the release config when CI exported a keystore.
    const buildType = /(buildTypes\s*\{[\s\S]*?release\s*\{[\s\S]*?)signingConfig signingConfigs\.debug/
    if (!buildType.test(src)) throw new Error("withReleaseSigning: release signingConfig line not found in app/build.gradle")
    src = src.replace(buildType, "$1signingConfig System.getenv('VK_KEYSTORE_PATH') ? signingConfigs.release : signingConfigs.debug")
    // 2. Declare signingConfigs.release next to the template's debug config.
    if (!/signingConfigs\s*\{/.test(src)) throw new Error("withReleaseSigning: signingConfigs block not found")
    src = src.replace(/signingConfigs\s*\{/, RELEASE_CONFIG)
    config.modResults.contents = src
    return config
  })
}
