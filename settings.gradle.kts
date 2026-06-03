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

// Android application: BLE activation, RFCOMM streaming, foreground service, Room
// storage, and the Compose UI. The Android Gradle Plugin needs the Android SDK at
// configuration time, so :app is included only when an SDK is available. This lets
// the pure-JVM CI (and any SDK-less checkout) configure and build :core-eeg alone.
// Locally, set ANDROID_HOME / ANDROID_SDK_ROOT, or add sdk.dir to local.properties.
val androidSdkAvailable = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    include(":app")
} else {
    println("[ebbflow] Android SDK not found — skipping :app (building :core-eeg only).")
}
