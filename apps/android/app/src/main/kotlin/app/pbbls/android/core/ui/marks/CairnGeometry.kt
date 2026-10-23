package app.pbbls.android.core.ui.marks

import app.pbbls.android.core.model.wrap.DayWeight
import kotlin.math.min

/**
 * A cairn's stones (#1207, D1), in dp: one per day that holds a moment, widest at the base. The
 * width is a display formula over the server's counts, `9 + 4.2·min(moments, 3) + 2.4·(intensity − 1)`,
 * a day without a pebble counting as intensity 1. Heights follow the stone's flattened outline.
 */
internal object CairnGeometry {
    const val BASE = 9f
    const val PER_MOMENT = 4.2f
    const val PER_INTENSITY = 2.4f
    const val MOMENTS_CAP = 3

    /** The stack never stands taller than this; a taller one keeps its widths and flattens its stones. */
    const val MAX_HEIGHT = 42f

    /** A cairn stone is a pebble outline pressed flat. */
    const val FLATTEN = 0.62f

    /** How much of a stone's height the one above sinks into it. */
    const val OVERLAP = 0.2f

    /** The live week's next stone, drawn dotted on top. */
    val NEXT_WIDTH = BASE + PER_MOMENT

    fun width(day: DayWeight): Float =
        BASE + PER_MOMENT * min(day.moments, MOMENTS_CAP) + PER_INTENSITY * (day.topIntensity.coerceIn(1, 3) - 1)

    /** Base first. */
    fun widths(days: List<DayWeight>): List<Float> = days.filter { it.moments > 0 }.map(::width).sortedDescending()

    fun height(
        width: Float,
        aspect: Float,
    ): Float = width / aspect * FLATTEN

    /** The stack's natural height: each stone sits [OVERLAP] of its height into the one below. */
    fun stackHeight(
        widths: List<Float>,
        aspect: Float,
    ): Float = widths.withIndex().sumOf { (i, w) -> (height(w, aspect) * if (i == 0) 1f else 1f - OVERLAP).toDouble() }.toFloat()

    /** The factor on every stone's height (never its width) that keeps the stack within [MAX_HEIGHT]. */
    fun squash(
        widths: List<Float>,
        aspect: Float,
        live: Boolean,
    ): Float {
        val natural = stackHeight(if (live) widths + NEXT_WIDTH else widths, aspect)
        return if (natural > MAX_HEIGHT) MAX_HEIGHT / natural else 1f
    }
}
