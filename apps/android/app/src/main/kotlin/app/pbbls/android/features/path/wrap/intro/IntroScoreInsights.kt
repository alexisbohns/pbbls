package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.model.wrap.ToneDay

/** The insights head alone, over a figure that draws nothing: the arrival's tick (D8; iOS `IntroScore.insightsHead`). */
fun IntroScore.insightsHead(): List<IntroBeat> = listOf(IntroBeat(IntroKey.Insights, 0.0, 0.0, IntroTick.arrive))

/**
 * The tone (iOS `IntroScore.tone`): its head (after the insights head when it leads them) and its sentence,
 * then the sweep left to right, a beat on each day with pebbles as the sweep reaches it.
 */
fun IntroScore.tone(
    days: List<ToneDay>,
    insights: Boolean,
): List<IntroBeat> {
    val beats = head(hasLead = true, insights = insights).toMutableList()
    val start = shift(insights) + IntroScore.SWEEP_AT
    beats += IntroBeat(IntroKey.Sweep, start, IntroScore.SWEEP_SPAN)
    days.forEachIndexed { index, day ->
        if (day.up + day.even + day.down <= 0) return@forEachIndexed
        val place = if (days.size > 1) index.toDouble() / (days.size - 1) else 0.0
        val reached = IntroScore.SWEEP_SPAN * RevealMotion.easeInOutInverse(place)
        beats += IntroBeat(IntroKey.Day(index), start + reached, IntroMotion.POP, IntroTick.light)
    }
    return beats
}

/**
 * The hours (iOS `IntroScore.hours`): its head and sentence, the dial's face, then [stones] stones in the
 * order of the time of day they happened ([ranks]).
 */
fun IntroScore.hours(
    stones: Int,
    insights: Boolean,
): List<IntroBeat> {
    val beats = head(hasLead = true, insights = insights).toMutableList()
    val shift = shift(insights)
    beats += IntroBeat(IntroKey.Dial, shift + IntroScore.DIAL_AT, IntroMotion.RISE)
    IntroCurve.times(stones, IntroScore.STONES_SPAN).forEachIndexed { rank, time ->
        beats += IntroBeat(IntroKey.Stone(rank), shift + IntroScore.STONES_AT + time, IntroMotion.POP, IntroTick.light)
    }
    return beats
}

/** Each stone's rank by the time of day it happened, from 00:00; ties keep their order. */
fun IntroScore.ranks(hours: List<Double>): List<Int> {
    val order = hours.indices.sortedWith(compareBy<Int> { hours[it] }.thenBy { it })
    val ranks = IntArray(hours.size)
    order.forEachIndexed { rank, index -> ranks[index] = rank }
    return ranks.toList()
}
