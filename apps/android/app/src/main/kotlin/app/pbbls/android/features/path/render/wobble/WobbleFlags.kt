package app.pbbls.android.features.path.render.wobble

import app.pbbls.android.BuildConfig

/**
 * Feature gate for the petroglyph wobble experiment (issue #555) — mirrors
 * iOS `WobbleFlags.swift`.
 *
 * Debug-only by construction: the spike evaluates the hand-carved look across
 * every pebble surface in dev builds, and `BuildConfig.DEBUG` is a constant
 * `false` in Release (R8 strips the gated branches) so it can never ship by
 * accident. Deleting the experiment means removing this folder and reverting
 * the flag-gated call sites ([app.pbbls.android.features.path.render
 * .PebbleStaticRender], [app.pbbls.android.features.path.render
 * .PebbleOutlineBackdrop], [app.pbbls.android.features.path.render
 * .GlyphImage]).
 */
internal object WobbleFlags {
    val isEnabled: Boolean = BuildConfig.DEBUG
}
