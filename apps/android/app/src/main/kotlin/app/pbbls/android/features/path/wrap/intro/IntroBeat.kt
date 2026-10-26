package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection

/** What enters in a month's intro (#1242; iOS `IntroSequence`): the cover, a section's card, or the end card (D14). */
sealed interface IntroSequence {
    data object Cover : IntroSequence

    data class Section(
        val section: MonthSection,
    ) : IntroSequence

    data object End : IntroSequence
}

/** One element of a sequence, named by what it is; the figures ask for these (iOS `IntroKey`, case for case). */
sealed interface IntroKey {
    // The cover: the month's name, the garland's course and leaf, the souls' glyphs, the texts; a day's
    // slot (its socket or its first seal), a stacked seal (day, index), a day's numeral.
    data object Kicker : IntroKey

    data object MonthName : IntroKey

    data object Year : IntroKey

    data object Course : IntroKey

    data object Leaf : IntroKey

    data object Summary : IntroKey

    data object Names : IntroKey

    data object Colophon : IntroKey

    data class Slot(
        val day: Int,
    ) : IntroKey

    data class Seal(
        val day: Int,
        val index: Int,
    ) : IntroKey

    data class Numeral(
        val day: Int,
    ) : IntroKey

    data class Soul(
        val index: Int,
    ) : IntroKey

    // Any card: the insights head, the head, the lead line, more, a row. The two heads are drawn from
    // the start (D8): their beats carry only the arrival's tick.
    data object Insights : IntroKey

    data object Header : IntroKey

    data object Lead : IntroKey

    data object More : IntroKey

    data class Row(
        val row: Int,
    ) : IntroKey

    // A person's row: its glyph, its name, its ring's line, a day on its ring (row, day).
    data class Glyph(
        val row: Int,
    ) : IntroKey

    data class Name(
        val row: Int,
    ) : IntroKey

    data class Ring(
        val row: Int,
    ) : IntroKey

    data class Dot(
        val row: Int,
        val day: Int,
    ) : IntroKey

    // The gem: the blank stone, the count, a facet, a family's name and share, the count's tally (1…n).
    data object Blank : IntroKey

    data object Count : IntroKey

    data class Facet(
        val id: String,
    ) : IntroKey

    data class Label(
        val slug: String,
    ) : IntroKey

    data class Tally(
        val pebble: Int,
    ) : IntroKey

    // Tone and hours: the sweep, the dial's face, a day of the chart, a stone by its rank in the day.
    data object Sweep : IntroKey

    data object Dial : IntroKey

    data class Day(
        val day: Int,
    ) : IntroKey

    data class Stone(
        val rank: Int,
    ) : IntroKey

    // The signatures (D10, D16, D17): a week's cairn stone by stone (row, stone), a column's date, a soul
    // of a row (row, index); a milestone's stamp, its impact, its date; a domain's gauge filling, full;
    // a week column's empty day (week, weekday) and a pebble of a day (week, weekday, index).
    data class Cairn(
        val row: Int,
        val stone: Int,
    ) : IntroKey

    data class Words(
        val row: Int,
    ) : IntroKey

    data class Mark(
        val row: Int,
        val index: Int,
    ) : IntroKey

    data class Stamp(
        val row: Int,
    ) : IntroKey

    data class Impact(
        val row: Int,
    ) : IntroKey

    data class Date(
        val row: Int,
    ) : IntroKey

    data class Gauge(
        val row: Int,
    ) : IntroKey

    data class Full(
        val row: Int,
    ) : IntroKey

    data class Rest(
        val week: Int,
        val weekday: Int,
    ) : IntroKey

    data class Pip(
        val week: Int,
        val weekday: Int,
        val index: Int,
    ) : IntroKey

    // The end card's replay (D14).
    data object Replay : IntroKey
}

private val SEAL_INTENSITIES = floatArrayOf(0.50f, 0.68f, 0.85f)

/** How firmly a beat ticks, 0…1 (iOS `IntroTick`; on Android, D18 maps sharpness to a primitive and intensity to its scale). */
data class IntroTick(
    val intensity: Float,
    val sharpness: Float,
) {
    companion object {
        /** An empty day on the garland. */
        val faint = IntroTick(0.22f, 0.25f)

        /** Rows, texts, glyphs, ring dots, tone days, hour stones. */
        val light = IntroTick(0.40f, 0.45f)
        val facet = IntroTick(0.60f, 0.55f)

        /** The garland's leaf, tying the ring. */
        val knot = IntroTick(0.75f, 0.35f)

        /** The gem's count. */
        val firm = IntroTick(0.90f, 0.70f)

        /** A card arriving on top (D8): firm, rounded. */
        val arrive = IntroTick(0.6f, 0.15f)

        /** A seal on the garland, firmer with its pebble's intensity, 1…3. */
        fun seal(intensity: Int) = IntroTick(SEAL_INTENSITIES[(intensity - 1).coerceIn(0, 2)], 0.60f)

        /** A week's cairn, stone [index] from the bottom: each a little firmer (D10). */
        fun cairn(index: Int) = IntroTick((0.32f + 0.07f * index).coerceAtMost(0.8f), 0.5f)
    }
}

/** A continuous vibration under a beat (D15): over its duration, from [from] to [to], at one sharpness. */
data class IntroSwell(
    val from: Float,
    val to: Float,
    val sharpness: Float,
)

/** One beat: [key] starts landing [at] seconds into its sequence and takes [duration]; it ticks [tick] as it starts and swells [swell] while it lands. */
data class IntroBeat(
    val key: IntroKey,
    val at: Double,
    val duration: Double,
    val tick: IntroTick? = null,
    val swell: IntroSwell? = null,
) {
    /** The beat [factor] times slower: its time and its duration (D9). */
    fun scaled(factor: Double) = copy(at = at * factor, duration = duration * factor)
}

/**
 * Each key's progress, linear 0…1, for a figure to draw (D6); figures apply [app.pbbls.android.core.designsystem.RevealMotion]'s curves.
 * The default everywhere is [settled]: today's render.
 */
fun interface IntroReveal {
    operator fun invoke(key: IntroKey): Float

    companion object {
        val settled = IntroReveal { 1f }
        val hidden = IntroReveal { 0f }
    }
}
