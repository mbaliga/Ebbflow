plugins {
    // kotlin.jvm (used by :core-eeg) and kotlin.android (used by :app) are the same
    // underlying Kotlin Gradle plugin artifact, so both must be declared here with a
    // single version to reconcile them on the shared plugin classpath; otherwise the
    // second module to request it fails with "already on the classpath with an
    // unknown version". The Android-only plugins (AGP, KSP, Compose compiler) are
    // declared in :app instead, so SDK-less builds never resolve them.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}
