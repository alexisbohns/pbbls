package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.LOW_TICK
import app.pbbls.android.features.path.wrap.intro.IntroPrimitiveKind.TICK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Tier C's later segments: each fired once, a floor ahead of its start (D18). */
class IntroSegmentQueueTest {
    private val july = YearMonth.of(2026, 7)
    private val domains = IntroSequence.Section(MonthSection.DOMAINS)
    private val start = 5_000_000_000L
    private val ms = 1_000_000L
    private val lead = IntroSegmentQueue.SEGMENT_LEAD_NANOS

    private val full = listOf(IntroPrimitive(TICK, 0.4f, 0), IntroPrimitive(LOW_TICK, 0.15f, 60))
    private val last = listOf(IntroPrimitive(TICK, 0.4f, 0))
    private val segments = listOf(IntroSegment(2_775.0, full), IntroSegment(4_900.25, last))

    private fun held() = IntroSegmentQueue().apply { hold(domains, july, start, segments) }

    @Test
    fun `a later segment's lead is the floor the scores leave after a swell's train`() {
        assertEquals(25_000_000L, lead)
        // A full gauge's train at the sections' tempo ends at least that far before the tick after it.
        val beats = IntroScore.domains(listOf(1.0), more = false).map { it.scaled(IntroTempo.standard.sections) }
        val swell = IntroHaptics.swells(beats).single()
        val full = beats.first { it.key == IntroKey.Full(0) }
        assertTrue(full.at - (swell.at + swell.sounding) >= IntroHaptics.FLOOR - 1e-12)
    }

    @Test
    fun `a segment is due a floor before its start and never twice`() {
        val queue = held()
        val at = start + 2_775 * ms
        assertNull(queue.due(domains, july, at - lead - 1))
        assertEquals(IntroSegmentQueue.Due(at, full), queue.due(domains, july, at - lead))
        assertNull(queue.due(domains, july, at - lead))
        assertNull(queue.due(domains, july, at))
        val next = start + 4_900_250_000L
        assertNull(queue.due(domains, july, next - lead - 1))
        assertEquals(IntroSegmentQueue.Due(next, last), queue.due(domains, july, next - lead))
        assertNull(queue.due(domains, july, next + 1_000 * ms))
    }

    @Test
    fun `a stalled frame fires only the latest segment due, its time not yet gone`() {
        val queue = held()
        assertEquals(last, queue.due(domains, july, start + 4_900 * ms)?.primitives)
        assertNull(queue.due(domains, july, start + 6_000 * ms))
    }

    @Test
    fun `a dropped card fires nothing more, and another card's segments stay`() {
        val queue = held()
        queue.hold(IntroSequence.Cover, july, start, segments)
        queue.drop(domains, july)
        assertNull(queue.due(domains, july, start + 3_000 * ms))
        // Another month's card of the same sequence is not that card.
        assertNull(queue.due(domains, july.plusMonths(1), start + 3_000 * ms))
        assertEquals(full, queue.due(IntroSequence.Cover, july, start + 3_000 * ms)?.primitives)
    }

    @Test
    fun `holding again replaces what the card held`() {
        val queue = held()
        val later = start + 10_000 * ms
        queue.hold(domains, july, later, segments.take(1))
        assertNull(queue.due(domains, july, start + 3_000 * ms))
        assertEquals(IntroSegmentQueue.Due(later + 2_775 * ms, full), queue.due(domains, july, later + 2_775 * ms - lead))
        assertNull(queue.due(domains, july, later + 6_000 * ms))
    }

    @Test
    fun `holding no segments stores nothing and drops what the card held`() {
        val queue = held()
        queue.hold(domains, july, start, emptyList())
        assertNull(queue.due(domains, july, start + 6_000 * ms))
    }

    @Test
    fun `a segment's lead delays its first primitive only`() {
        val due = IntroSegmentQueue.Due(start, full)
        assertEquals(listOf(37, 60), due.led(37).map { it.pauseMs })
        assertEquals(full, due.led(0))
    }
}
