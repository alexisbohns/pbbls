package app.pbbls.android.features.path.spine.logic

import kotlin.math.max
import kotlin.math.min

/** A card's width and approximate height in dp, and the height cap it was fitted to. */
data class CardSize(
    val width: Float,
    val height: Float,
    val cap: Float,
)

/**
 * Port of `card-size.ts` (#1052, spec §Card sizing), phone caps only (spec
 * D4). The card is sized to its photo's own ratio and never crops it.
 *
 * The chrome (index, rail, mat, title, souls strip) is treated as a fixed
 * 123 dp vertical and 95 dp horizontal, so with `cap` the card's height cap:
 *
 *   W = min(column, (cap − 123) × ratio + 95)      H = 123 + (W − 95) / ratio
 *
 * plus the 180 dp floor (a title needs that width, even if the card then
 * exceeds its cap). Until a photo's ratio is known the card is square (D14).
 */
object CardSizes {
    /** Fixed chrome around the photo, in dp. */
    const val CHROME_VERTICAL = 123f
    const val CHROME_HORIZONTAL = 95f

    /** Below this width a title no longer fits. */
    const val MIN_WIDTH = 180f

    /** The ratio a card holds while its photo's real ratio is unknown (D14). */
    const val PENDING_RATIO = 1f

    private val CAP = mapOf(1 to 330f, 2 to 370f, 3 to 400f)

    /** The painted bottom edge's thickness, by intensity. */
    private val EDGE = mapOf(1 to 2f, 2 to 3.5f, 3 to 5.5f)

    private fun checkIntensity(intensity: Int) {
        require(intensity in 1..3) { "intensity must be 1..3, was $intensity" }
    }

    /** The card's height cap in dp. */
    fun cap(intensity: Int): Float {
        checkIntensity(intensity)
        return CAP.getValue(intensity)
    }

    fun edge(intensity: Int): Float {
        checkIntensity(intensity)
        return EDGE.getValue(intensity)
    }

    /**
     * The card's width and approximate height for a photo [ratio] (width /
     * height) in a content column [column] dp wide. The height is exact only
     * when the card unit has reached its ceiling (card ≥ 308 dp); narrower
     * cards drift by up to about 10 dp, as in the mock.
     */
    fun size(
        intensity: Int,
        ratio: Float?,
        column: Float,
    ): CardSize {
        val cap = cap(intensity)
        val safeRatio = if (ratio != null && ratio.isFinite() && ratio > 0f) ratio else PENDING_RATIO
        val fitted = min(column, (cap - CHROME_VERTICAL) * safeRatio + CHROME_HORIZONTAL)
        val width = max(fitted, min(MIN_WIDTH, column))
        val height = CHROME_VERTICAL + (width - CHROME_HORIZONTAL) / safeRatio
        return CardSize(width = width, height = height, cap = cap)
    }
}
