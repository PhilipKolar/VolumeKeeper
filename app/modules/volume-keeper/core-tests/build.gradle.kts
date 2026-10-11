plugins {
    // Keep in step with React Native's Kotlin (node_modules/react-native/gradle/libs.versions.toml).
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        kotlin.srcDir("../android/src/main/java/com/philkolar/volumekeeper/core")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
