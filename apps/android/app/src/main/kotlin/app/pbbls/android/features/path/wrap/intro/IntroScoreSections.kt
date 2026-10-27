package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.model.wrap.DaySet

/**
 * A section's head (iOS `IntroScore.head`): the insights head first when it leads tone or hours, then the
 * head, then the lead line if any. One arrival per card: the insights head ticks it when it leads, the head
 * otherwise. Both heads are drawn from the start (D8): their beats carry only the tick.
 */
fun IntroScore.head(
    hasLead: Boolean,
    insights: Boolean,
): List<IntroBeat> {
    val beats = mutableListOf<IntroBeat>()
    if (insights) beats += IntroBeat(IntroKey.Insights, 0.0, 0.0, IntroTick.arrive)
    val shift = shift(insights)
    beats += IntroBeat(IntroKey.Header, shift, 0.0, if (insights) null else IntroTick.arrive)
    if (hasLead) beats += IntroBeat(IntroKey.Lead, shift + IntroScore.LEAD_AT, IntroMotion.RISE)
    return beats
}

/** How much later a section's own beats come: the insights head's shift when it leads. */
fun IntroScore.shift(insights: Boolean): Double = if (insights) IntroScore.INSIGHTS_SHIFT else 0.0

/**
 * The persons (iOS `IntroScore.persons`): per row its glyph, its name, then its ring's line drawn from the
 * 1st, a beat on each day present as the line reaches it, in day order; the next row once the ring has
 * closed; *more* as the last ring closes. With nobody named, the head and the invitation ([hasLead]).
 */
fun IntroScore.persons(
    rings: List<DaySet>,
    hasLead: Boolean,
): List<IntroBeat> {
    val beats = head(hasLead, insights = false).toMutableList()
    var at = IntroScore.ROWS_AT
    rings.forEachIndexed { row, days ->
        beats += IntroBeat(IntroKey.Glyph(row), at, IntroMotion.POP, IntroTick.light)
        beats += IntroBeat(IntroKey.Name(row), at + IntroScore.PART_GAP, IntroMotion.RISE, IntroTick.light)
        val ring = at + 2 * IntroScore.PART_GAP
        beats += IntroBeat(IntroKey.Ring(row), ring, IntroScore.RING_SPAN)
        // In day order: a set's own order is no promise (on iOS it changed with each run, and the half-drawn ring with it).
        days.days.sorted().forEach { day ->
            val reached = IntroScore.RING_SPAN * RevealMotion.easeInOutInverse(dotPlace(day, days.length))
            beats += IntroBeat(IntroKey.Dot(row, day), ring + reached, IntroMotion.POP, IntroTick.light)
        }
        if (row == rings.lastIndex) beats += IntroBeat(IntroKey.More, ring + IntroScore.RING_SPAN, IntroMotion.RISE)
        at = ring + IntroScore.RING_SPAN + IntroScore.NEXT_ROW
    }
    return beats
}

/** How far round its ring a day sits, 0…1: the middle of its slot, the ring opening before the 1st. */
fun IntroScore.dotPlace(
    day: Int,
    length: Int,
): Double = if (length > 0) (day + 0.5) / length else 0.0
