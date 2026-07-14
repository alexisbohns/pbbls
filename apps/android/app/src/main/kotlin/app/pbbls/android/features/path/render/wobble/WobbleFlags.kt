package app.pbbls.android.features.path.render.wobble

import app.pbbls.android.BuildConfig

/**
 * Feature gate for the petroglyph wobble experiment (issue #555) — mirrors
 * iOS `WobbleFlags.swift`, with one Android divergence (2026-07-14 decisions
 * log): the gate reads `BuildConfig.WOBBLE_ENABLED` instead of `DEBUG`.
 *
 * Debug builds compile it to a constant `true` (the iOS `#if DEBUG` analog).
 * Release builds compile it to `false` **unless** the build sets
 * `WOBBLE_ENABLED=true` through the D8 env/`secrets.properties` chain — which
 * only the Play internal-testing workflow (`android-release.yml`) does,
 * because the maintainer's device loop has no debug channel, unlike
 * iOS/Xcode. Forks, local release builds and any future production pipeline
 * stay wobble-free by default. Either way the value is a compile-time
 * constant, so R8 strips the dormant branch.
 *
 * Deleting the experiment means removing this folder and reverting the
 * flag-gated call sites ([app.pbbls.android.features.path.render
 * .PebbleStaticRender], [app.pbbls.android.features.path.render
 * .PebbleOutlineBackdrop], [app.pbbls.android.features.path.render
 * .GlyphImage]) plus the `WOBBLE_ENABLED` plumbing in `build.gradle.kts` /
 * `android-release.yml`.
 */
internal object WobbleFlags {
    val isEnabled: Boolean = BuildConfig.WOBBLE_ENABLED
}
