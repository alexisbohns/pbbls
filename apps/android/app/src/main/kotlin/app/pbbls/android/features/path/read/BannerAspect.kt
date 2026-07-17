package app.pbbls.android.features.path.read

import kotlin.math.abs

/**
 * Aspect-ratio bucket chosen for a pebble's snap on the read view — ports iOS
 * `BannerAspect.swift`. The snap's width/height ratio snaps to the nearest of
 * three fixed buckets — 4:3 (landscape), 1:1, 3:4 (portrait) — so the imported
 * photo reads at a natural shape instead of a forced strip (issue #599).
 *
 * These buckets replace the earlier 16:9/4:3/1:1 set: the redesigned page
 * frames the whole picture (portrait included) with the Petroglyph overlapping
 * its top-right, so a tall 3:4 crop is now first-class and the wide 16:9 strip
 * is gone.
 *
 * Pure value type; trivially unit-testable.
 */
enum class BannerAspect(
    /** Width / height for the bucket. */
    val ratio: Float,
) {
    FOUR_THREE(4f / 3f),
    SQUARE(1f),
    THREE_FOUR(3f / 4f),
    ;

    companion object {
        /**
         * The bucket whose ratio is closest to [ratio] (absolute distance).
         * Very wide sources collapse to [FOUR_THREE] and very tall ones to
         * [THREE_FOUR] since those are the extreme candidates — no special case.
         */
        fun nearest(ratio: Float): BannerAspect = entries.minByOrNull { abs(it.ratio - ratio) } ?: SQUARE
    }
}
