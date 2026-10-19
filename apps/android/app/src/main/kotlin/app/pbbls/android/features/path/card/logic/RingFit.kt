package app.pbbls.android.features.path.card.logic

import java.util.Locale
import kotlin.math.floor

/**
 * How a domain's name rings a postmark (spec §Verso 4): [text] is the name in
 * capitals, repeated with " · " after each, set at [sizeSp] with [trackingPx]
 * added after every character so the ring closes exactly and the seam is an
 * ordinary gap.
 */
data class RingFit(
    val text: String,
    val repetitions: Int,
    val sizeSp: Float,
    val trackingPx: Float,
)

object RingFits {
    /** Tried in order; a name that cannot ring twice at the last is set once. */
    val SIZES_SP = listOf(8.5f, 8f, 7.5f)
    const val SEPARATOR = " · "
    private const val MIN_REPETITIONS = 2

    /**
     * @param circumferencePx the circle the text's baseline-centre runs on.
     * @param measure the advance of a string at a size in sp, in px: a
     *   `TextMeasurer` on device, a fake in tests.
     */
    fun fit(
        name: String,
        circumferencePx: Float,
        locale: Locale,
        measure: (text: String, sizeSp: Float) -> Float,
    ): RingFit {
        val unit = name.uppercase(locale) + SEPARATOR
        for (size in SIZES_SP) {
            val width = measure(unit, size)
            val repetitions = floor(circumferencePx / width).toInt()
            if (repetitions >= MIN_REPETITIONS) {
                val text = unit.repeat(repetitions)
                return RingFit(text, repetitions, size, (circumferencePx - measure(text, size)) / text.length)
            }
        }
        val size = SIZES_SP.last()
        return RingFit(unit, 1, size, ((circumferencePx - measure(unit, size)) / unit.length).coerceAtLeast(0f))
    }
}
