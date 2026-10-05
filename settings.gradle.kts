rootProject.name = "pdf2llm"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK requested by `jvmToolchain(...)` if it is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
