package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.designsystem.RevealMotion

/**
 * A week's column in the intro (D17; iOS `IntroWeekColumn`): each weekday's pebbles shown, Monday first (null
 * outside the month, 0 a day without a pebble), and its cairn's stones (0, an empty week).
 */
data class IntroWeekColumn(
    val days: List<Int?>,
    val stones: Int,
)

/** A milestone in the intro (iOS `IntroMilestoneShape`): its pebble's intensity, 1…3, and the souls it shows. */
data class IntroMilestoneShape(
    val intensity: Int,
    val souls: Int,
)

/**
 * The weeks on a card (D17; iOS `IntroScore.weekColumns`): one week at a time, its date, then its days down
 * the column, a day without a pebble a silent slot and each pebble with a tick, then its cairn stone by
 * stone, each stone a little firmer; an empty week's dotted stone on a faint tick.
 */
fun IntroScore.weekColumns(columns: List<IntroWeekColumn>): List<IntroBeat> {
    val beats = head(hasLead = false, insights = false).toMutableList()
    var at = IntroScore.ROWS_AT
    columns.forEachIndexed { week, column ->
        beats += IntroBeat(IntroKey.Words(week), at, IntroMotion.RISE, IntroTick.light)
        var part = at + IntroScore.PART_GAP
        column.days.forEachIndexed { day, pebbles ->
            if (pebbles == null) return@forEachIndexed
            if (pebbles == 0) {
                beats += IntroBeat(IntroKey.Rest(week, day), part, IntroMotion.POP)
            } else {
                repeat(pebbles) { pip ->
                    if (pip > 0) part += IntroScore.PIP_GAP
                    beats += IntroBeat(IntroKey.Pip(week, day, pip), part, IntroMotion.POP, IntroTick.light)
                }
            }
            part += IntroScore.DAY_GAP
        }
        part += IntroScore.CAIRN_AFTER - IntroScore.DAY_GAP
        if (column.stones == 0) beats += IntroBeat(IntroKey.Cairn(week, 0), part, IntroMotion.POP, IntroTick.faint)
        repeat(column.stones) { stone ->
            if (stone > 0) part += IntroScore.STONE_GAP
            beats += IntroBeat(IntroKey.Cairn(week, stone), part, IntroMotion.POP, IntroTick.cairn(stone))
        }
        at = part + IntroScore.NEXT_WEEK
    }
    return beats
}

/**
 * The milestones (iOS `IntroScore.milestones`): each stone stamps, ticking at its impact as firmly as its
 * pebble is intense, then its date, its name and its souls one by one, then the next.
 */
fun IntroScore.milestones(rows: List<IntroMilestoneShape>): List<IntroBeat> {
    val beats = head(hasLead = false, insights = false).toMutableList()
    var at = IntroScore.ROWS_AT
    rows.forEachIndexed { row, milestone ->
        beats += IntroBeat(IntroKey.Stamp(row), at, IntroScore.STAMP_SPAN)
        beats +=
            IntroBeat(IntroKey.Impact(row), at + IntroMotion.STAMP_IMPACT * IntroScore.STAMP_SPAN, 0.0, IntroTick.seal(milestone.intensity))
        var part = at + IntroScore.STAMP_SPAN
        beats += IntroBeat(IntroKey.Date(row), part, IntroMotion.RISE, IntroTick.light)
        part += IntroScore.PART_GAP
        beats += IntroBeat(IntroKey.Name(row), part, IntroMotion.RISE, IntroTick.light)
        part = souls(row, milestone.souls, part, beats)
        at = part + IntroScore.NEXT_MILESTONE
    }
    return beats
}

/**
 * The domains (iOS `IntroScore.domains`): each name and count, then its gauge filling for longer the fuller
 * it is, swelling as it fills and ticking as it stops, then the next; *more* with the last.
 */
fun IntroScore.domains(
    shares: List<Double>,
    more: Boolean,
): List<IntroBeat> {
    val beats = head(hasLead = false, insights = false).toMutableList()
    var at = IntroScore.ROWS_AT
    var last = IntroScore.ROWS_AT
    shares.forEachIndexed { row, share ->
        beats += IntroBeat(IntroKey.Row(row), at, IntroMotion.RISE, IntroTick.light)
        val fill = fillSpan(share)
        val gauge = at + IntroScore.PART_GAP
        beats +=
            IntroBeat(
                IntroKey.Gauge(row),
                gauge,
                fill,
                swell = IntroSwell(IntroScore.SWELL_FROM, swellTo(share), IntroScore.SWELL_SHARPNESS),
            )
        last = gauge + fill
        beats += IntroBeat(IntroKey.Full(row), last, 0.0, IntroTick.light)
        at = last + IntroScore.NEXT_ROW
    }
    if (more && shares.isNotEmpty()) beats += IntroBeat(IntroKey.More, last, IntroMotion.RISE)
    return beats
}

/** The end card (D14; iOS `IntroScore.end`): its arrival, then its replay button. */
fun IntroScore.end(): List<IntroBeat> =
    listOf(
        IntroBeat(IntroKey.Header, 0.0, 0.0, IntroTick.arrive),
        IntroBeat(IntroKey.Replay, IntroScore.ROWS_AT, IntroMotion.RISE, IntroTick.light),
    )

/** How long a gauge fills: a fuller one takes longer. */
fun IntroScore.fillSpan(share: Double): Double = 0.25 + 0.45 * RevealMotion.clamp(share)

/** How strong a gauge's swell ends: a fuller one ends stronger. */
fun IntroScore.swellTo(share: Double): Float = 0.25f + 0.5f * RevealMotion.clamp(share).toFloat()

/** A row's souls one by one, a part gap after [after], into [beats]; returns the last part's time. */
private fun souls(
    row: Int,
    count: Int,
    after: Double,
    beats: MutableList<IntroBeat>,
): Double {
    if (count <= 0) return after
    var part = after + IntroScore.PART_GAP
    repeat(count) { soul ->
        if (soul > 0) part += IntroScore.MARK_GAP
        beats += IntroBeat(IntroKey.Mark(row, soul), part, IntroMotion.POP, IntroTick.light)
    }
    return part
}
