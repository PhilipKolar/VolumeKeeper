// Standalone JVM project that compiles the pure Kotlin decision core (../android/src/main/java/
// com/philkolar/volumekeeper/core) and runs its unit tests — no Android SDK, no React Native.
// CI's "kotlin-core" job and scripts/smoke-test.sh run `./gradlew test` here.
rootProject.name = "volumekeeper-core-tests"
