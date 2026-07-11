// AGP 9 ships built-in Kotlin support, but at a KGP version (2.2.x) older than
// the Kotlin we pin, and it does NOT bundle the Compose compiler. Force the
// pinned KGP onto the buildscript classpath so the built-in Kotlin compiler and
// the Compose compiler plugin (pinned to the same version in libs.versions.toml)
// agree. See docs/superpowers/specs/2026-07-10-android-bootstrap-design.md (D2).
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // Keep in sync with `kotlin` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

// Plugins are declared here (resolved once for the whole build) and applied in
// the module that needs them — the single :app module. The Kotlin Android
// plugin is deliberately absent: AGP 9 provides it built-in, and applying it
// explicitly would fail with a "duplicate kotlin extension" error.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.screenshot) apply false
}
