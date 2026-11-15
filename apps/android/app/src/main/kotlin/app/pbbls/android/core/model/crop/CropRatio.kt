package app.pbbls.android.core.model.crop

import kotlin.math.abs
import kotlin.math.ln

/**
 * The five crop formats (#1659, iOS `CropRatio`, spec D3). No free crop: a moment's photo is
 * always one of these shapes. [label] is the ratio a chip writes inside its shape.
 */
enum class CropRatio(
    val width: Int,
    val height: Int,
) {
    FOUR_FIVE(4, 5),
    THREE_FOUR(3, 4),
    SQUARE(1, 1),
    FOUR_THREE(4, 3),
    FIVE_FOUR(5, 4),
    ;

    /** "4:5", in the chip and in logs. */
    val label: String get() = "$width:$height"

    /** Width over height. */
    val value: Double get() = width.toDouble() / height

    /** The same format turned a quarter (spec D5). */
    val paired: CropRatio
        get() =
            when (this) {
                FOUR_FIVE -> FIVE_FOUR
                THREE_FOUR -> FOUR_THREE
                SQUARE -> SQUARE
                FOUR_THREE -> THREE_FOUR
                FIVE_FOUR -> FOUR_FIVE
            }

    companion object {
        /**
         * The format nearest [ratio] (width over height), compared in log space so a portrait and
         * its landscape pair sit equally far from square. On a tie the earlier entry wins, and the
         * portrait ones come first.
         */
        fun closest(ratio: Double): CropRatio {
            if (ratio <= 0 || !ratio.isFinite()) return SQUARE
            val target = ln(ratio)
            var best = SQUARE
            var bestDistance = Double.POSITIVE_INFINITY
            for (candidate in entries) {
                val distance = abs(ln(candidate.value) - target)
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
            }
            return best
        }
    }
}
