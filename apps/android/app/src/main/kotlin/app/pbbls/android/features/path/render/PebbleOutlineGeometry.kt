package app.pbbls.android.features.path.render

import app.pbbls.android.features.path.models.ValenceSizeGroup

/**
 * Layout constants for composing [PebbleOutlineBackdrop] underneath
 * [PebbleSvg] — ports iOS `PebbleOutlineGeometry.swift`. The outline viewBox
 * is intentionally ~1.35× the pebble's viewBox so the silhouette frames the
 * artwork with ~12–13% margin per edge; the pebble render is down-scaled
 * explicitly to land at the correct relative size inside the backdrop.
 */
object PebbleOutlineGeometry {
    private data class Dimensions(
        val width: Float,
        val height: Float,
    )

    /**
     * Outline frame dimensions from the issue-#473 SVG assets (the
     * width/height attributes on each `res/raw/outline_<size>_<polarity>.svg`).
     * Keep in sync with the SVG files manually.
     */
    private val outlineSizes =
        mapOf(
            ValenceSizeGroup.SMALL to Dimensions(337f, 270f),
            ValenceSizeGroup.MEDIUM to Dimensions(350f, 350f),
            ValenceSizeGroup.LARGE to Dimensions(335f, 400f),
        )

    /**
     * Pebble composed-SVG canvas dims per size. Mirrors
     * `packages/supabase/supabase/functions/_shared/engine/layout.ts`.
     */
    private val pebbleSizes =
        mapOf(
            ValenceSizeGroup.SMALL to Dimensions(250f, 200f),
            ValenceSizeGroup.MEDIUM to Dimensions(260f, 260f),
            ValenceSizeGroup.LARGE to Dimensions(260f, 310f),
        )

    /**
     * Linear scale factor applied to the pebble render so it fits inside the
     * larger backdrop viewBox. Width ratio only — the per-size aspect ratios
     * match, so the axes agree.
     */
    fun pebbleScale(size: ValenceSizeGroup): Float = pebbleSizes.getValue(size).width / outlineSizes.getValue(size).width

    /** Aspect ratio (width / height) of the outline viewBox. */
    fun aspectRatio(size: ValenceSizeGroup): Float = outlineSizes.getValue(size).let { it.width / it.height }
}
