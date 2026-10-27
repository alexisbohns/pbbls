package app.pbbls.android.features.path.wrap.intro

import java.time.YearMonth

/**
 * Tier C's later segments, per card (D18): each is due [SEGMENT_LEAD_NANOS] before its start, so the lead
 * its vibration waits starts it on its scored time. That lead is a floor, the gap the scores leave between a
 * swell's train and the tick after it: firing earlier would supersede the train's loudest ticks while they
 * still sound on a vibrator that keeps time. Pure, so the timing is tested. Main thread only.
 */
class IntroSegmentQueue {
    /** A segment to play: its [primitives] start at [startNanos], `System.nanoTime()`'s timebase. */
    data class Due(
        val startNanos: Long,
        val primitives: List<IntroPrimitive>,
    ) {
        /** Its primitives, the first delayed by [leadMs]. */
        fun led(leadMs: Int): List<IntroPrimitive> =
            primitives.mapIndexed { i, p -> if (i == 0) p.copy(pauseMs = p.pauseMs + leadMs) else p }
    }

    private class Card(
        val segments: List<Due>,
        var next: Int = 0,
    )

    private val cards = mutableMapOf<Pair<YearMonth, IntroSequence>, Card>()

    /**
     * Holds [segments] for a card starting at [startNanos], each at its own start; replaces what the card held.
     * No segments store nothing.
     */
    fun hold(
        sequence: IntroSequence,
        month: YearMonth,
        startNanos: Long,
        segments: List<IntroSegment>,
    ) {
        if (segments.isEmpty()) {
            cards.remove(month to sequence)
        } else {
            cards[month to sequence] = Card(segments.map { Due(startNanos + (it.atMs * 1e6).toLong(), it.primitives) })
        }
    }

    /**
     * The card's segment to play at [nanos], or null: the latest one due, none twice. One fired after its
     * start (a stalled frame) plays whole and late, by the stall; one a stall passed altogether is skipped,
     * its time gone, for the next supersedes it anyway.
     */
    fun due(
        sequence: IntroSequence,
        month: YearMonth,
        nanos: Long,
    ): Due? {
        val card = cards[month to sequence] ?: return null
        var past = card.next
        while (past < card.segments.size && nanos >= card.segments[past].startNanos - SEGMENT_LEAD_NANOS) past += 1
        val fire = if (past > card.next) card.segments[past - 1] else null
        card.next = past
        if (past == card.segments.size) cards.remove(month to sequence)
        return fire
    }

    /** Drops what the card still held. */
    fun drop(
        sequence: IntroSequence,
        month: YearMonth,
    ) {
        cards.remove(month to sequence)
    }

    companion object {
        /** How far ahead of its start a later segment fires: a floor, the gap after a swell's train. */
        val SEGMENT_LEAD_NANOS = (IntroHaptics.FLOOR * 1e9).toLong()
    }
}
