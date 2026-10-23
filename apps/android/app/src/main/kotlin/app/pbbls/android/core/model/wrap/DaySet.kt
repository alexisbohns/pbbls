package app.pbbls.android.core.model.wrap

/** The days of a span (a week of 7, or a month) that something touches, as 0-based indices. */
data class DaySet(
    val length: Int,
    val days: Set<Int>,
) {
    operator fun contains(day: Int): Boolean = day in days

    val count: Int get() = days.size
}

/** One day of a cairn: how many moments it holds and its strongest pebble's intensity (0 without a pebble). */
data class DayWeight(
    val moments: Int,
    val topIntensity: Int,
)

internal fun List<Int>.toDaySet(length: Int): DaySet = DaySet(length, filter { it in 0 until length }.toSet())

internal fun List<List<Int>>.toDayWeights(): List<DayWeight> = map { DayWeight(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }) }
