package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble

/**
 * The beats of each sequence of a month's intro (#1242; iOS `IntroScore`, value for value). Pure, over
 * what the views already show after their own lookups filtering, so a beat never names a row the page
 * doesn't draw. Every constant lives here; the sections' builders are extensions in the files beside it.
 */
object IntroScore {
    const val NAME_AT = 0.12
    const val YEAR_AT = 0.24
    const val GARLAND_AT = 0.4
    const val GARLAND_SPAN = 1.5
    const val STACK_GAP = 0.035
    const val LEAF_AFTER = 0.12
    const val SOULS_AFTER = 0.2
    const val SOUL_GAP = 0.06
    const val LINE_GAP = 0.14

    // The sections (D8, iOS IntroScore+Sections).
    const val LEAD_AT = 0.16
    const val ROWS_AT = 0.32

    /** When the insights head leads tone or hours, the rest comes this much later. */
    const val INSIGHTS_SHIFT = 0.16
    const val PART_GAP = 0.09
    const val RING_SPAN = 0.45
    const val NEXT_ROW = 0.06

    // The gem (D10, iOS IntroScore+Gem).
    const val BLANK_AT = 0.32
    const val COUNT_AT = 0.4
    const val FACET_GAP = 0.045
    const val FAMILY_PAUSE = 0.22
    const val COUNT_AFTER = 0.16

    /** When a sequence has landed: its latest beat's end. */
    fun duration(beats: List<IntroBeat>): Double = beats.maxOfOrNull { it.at + it.duration } ?: 0.0

    /**
     * The cover: the month's name; the garland's days from the 1st (an empty day's socket, or its seals,
     * a stacked one a step after the first) with their numerals, under the course drawing round; the
     * leaf; then the souls' glyphs and the [lines] shown (⊆ summary, names, colophon).
     */
    fun cover(
        days: List<CoverDay>,
        daysInMonth: Int,
        numbered: List<Int>,
        souls: Int,
        lines: List<IntroKey>,
    ): List<IntroBeat> {
        val beats =
            mutableListOf(
                IntroBeat(IntroKey.Kicker, 0.0, IntroMotion.RISE),
                IntroBeat(IntroKey.MonthName, NAME_AT, IntroMotion.RISE, IntroTick.light),
                IntroBeat(IntroKey.Year, YEAR_AT, IntroMotion.RISE),
                IntroBeat(IntroKey.Course, GARLAND_AT, GARLAND_SPAN),
            )
        val seals = days.distinctBy { it.day }.associate { it.day to it.pebbles }
        val numerals = numbered.toSet()
        val slots = IntroCurve.times(daysInMonth, GARLAND_SPAN).map { GARLAND_AT + it }
        val leafAt = GARLAND_AT + GARLAND_SPAN + LEAF_AFTER
        slots.forEachIndexed { day, at ->
            val pebbles = seals[day].orEmpty()
            val next = slots.getOrElse(day + 1) { leafAt }
            val stacked = stackedSeals(pebbles, day, at, next)
            // A day whose stacked seals can't all tick taps once, at its strongest seal.
            val silenced = stacked.any { it.tick == null }
            val firmness = if (silenced) pebbles.maxOfOrNull { it.intensity } else pebbles.firstOrNull()?.intensity
            beats += IntroBeat(IntroKey.Slot(day), at, IntroMotion.POP, firmness?.let(IntroTick::seal) ?: IntroTick.faint)
            beats += stacked
            if (day in numerals) beats += IntroBeat(IntroKey.Numeral(day), at, IntroMotion.RISE)
        }
        var at = leafAt
        beats += IntroBeat(IntroKey.Leaf, at, IntroMotion.POP, IntroTick.knot)
        at += SOULS_AFTER
        repeat(souls) { soul ->
            beats += IntroBeat(IntroKey.Soul(soul), at, IntroMotion.POP, IntroTick.light)
            at += SOUL_GAP
        }
        lines.forEach { line ->
            beats += IntroBeat(line, at, IntroMotion.RISE, IntroTick.light)
            at += LINE_GAP
        }
        return beats
    }

    /**
     * A day's stacked seals, a step apart after its slot. Each ticks only clear of the tick before it and
     * of the next tick in the score (the next day's slot, or the leaf after the last day), by the floor:
     * otherwise the floor would drop it or that slot.
     */
    private fun stackedSeals(
        pebbles: List<CoverPebble>,
        day: Int,
        slot: Double,
        next: Double,
    ): List<IntroBeat> {
        var lastTick = slot
        return pebbles.withIndex().drop(1).map { (index, pebble) ->
            val at = slot + STACK_GAP * index
            val fits = at - lastTick >= IntroHaptics.FLOOR && next - at >= IntroHaptics.FLOOR
            if (fits) lastTick = at
            IntroBeat(IntroKey.Seal(day, index), at, IntroMotion.POP, if (fits) IntroTick.seal(pebble.intensity) else null)
        }
    }
}
