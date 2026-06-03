pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ebbflow"

// Pure-JVM EEG core (packet parsing, constants, DSP). Buildable/testable without
// the Android SDK. The Android :app module (Bluetooth, foreground service, Room,
// Compose UI) is added on top and depends on this module.
include(":core-eeg")
