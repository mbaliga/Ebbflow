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

// The EEG core (parser + FocusEstimator) is the PROPRIETARY Baseline engine and lives in
// the private `baseline` repo (D-S), NOT here — Ebbflow is the OPEN app that licenses it.
// It is therefore not an `include`d module; :app consumes it as a licensed binary once the
// engine publishes one (see app/build.gradle.kts). Until then :app won't build — by design.

// Android application: BLE activation, RFCOMM streaming, foreground service, Room
// storage, and the Compose UI. The Android Gradle Plugin needs the Android SDK at
// configuration time, so :app is included only when local.properties declares an
// sdk.dir. This is deliberately *not* keyed off ANDROID_HOME/ANDROID_SDK_ROOT:
// GitHub's hosted CI runners set those env vars, which would drag the Android build
// into the pure-JVM CI. Android Studio writes local.properties automatically; for a
// CLI build run:  echo "sdk.dir=$ANDROID_HOME" > local.properties
val androidSdkAvailable = file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    include(":app")
} else {
    println("[ebbflow] No sdk.dir in local.properties — skipping :app (building :core-eeg only).")
}
