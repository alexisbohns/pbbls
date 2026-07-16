package app.pbbls.android.features.path.read

import kotlin.math.abs

/**
 * Banner aspect-ratio bucket chosen for a source image — ports iOS
 * `BannerAspect.swift`. The pebble read banner snaps the source's
 * width/height ratio to the nearest of three fixed buckets — 16:9, 4:3, 1:1 —
 * so portrait or near-square uploads don't get cropped to a forced landscape
 * strip. Pure value type; trivially unit-testable.
 */
enum class BannerAspect(
    /** Width / height for the bucket. */
    val ratio: Float,
) {
    SIXTEEN_NINE(16f / 9f),
    FOUR_THREE(4f / 3f),
    SQUARE(1f),
    ;

    companion object {
        /**
         * The bucket whose ratio is closest to [ratio] (absolute distance).
         * Portrait sources (`ratio < 1`) always bucket to [SQUARE] since 1.0
         * is the smallest candidate — no special case.
         */
        fun nearest(ratio: Float): BannerAspect = entries.minByOrNull { abs(it.ratio - ratio) } ?: SIXTEEN_NINE
    }
}
