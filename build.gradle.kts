plugins {
    // kotlin.jvm (used by :core-eeg-community) and kotlin.android (used by :app) are the same
    // underlying Kotlin Gradle plugin artifact, so both must be declared here with a
    // single version to reconcile them on the shared plugin classpath; otherwise the
    // second module to request it fails with "already on the classpath with an
    // unknown version". KSP and the Compose compiler are declared in :app only.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    // AGP has to sit on the same (root) plugin classloader as the Kotlin plugin above:
    // kotlin.android's KotlinAndroidTarget links against AGP classes such as
    // com.android.build.gradle.api.BaseVariant, and a child-only AGP is invisible to it,
    // so configuring :app failed with "Could not generate a decorated class for type
    // KotlinAndroidTarget" whenever local.properties had sdk.dir. `apply false` applies
    // nothing Android and needs no SDK, but it does mean SDK-less builds (the pure-JVM CI)
    // now resolve and download the AGP plugin artifact as well; they still never apply it.
    alias(libs.plugins.android.application) apply false
}
