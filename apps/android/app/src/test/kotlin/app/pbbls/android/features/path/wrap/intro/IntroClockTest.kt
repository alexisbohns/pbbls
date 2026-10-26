package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** iOS `IntroClockTests` and `IntroLandingTests` (#1241 D3, D4, D11, §Lifecycle), on the deck's clock. */
class IntroClockTest {
    private val october = YearMonth.of(2026, 10)
    private val base = 1_000_000_000_000L
    private val weeks = IntroSequence.Section(MonthSection.WEEKS)
    private val persons = IntroSequence.Section(MonthSection.PERSONS)

    /** Lands at 0.6 s (0.32 + 0.28). */
    private val beats =
        listOf(
            IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light),
            IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light),
        )

    private fun at(seconds: Double) = base + (seconds * 1e9).toLong()

    private val start = base + IntroClock.LEAD_NANOS

    private fun after(seconds: Double) = start + (seconds * 1e9).toLong()

    /** At tempo 1, so the beats play at the times they name. */
    private fun clock(
        ledger: IntroLedger = IntroLedger(),
        haptics: HapticsSpy? = null,
        month: YearMonth = october,
    ) = IntroClock(month, ledger, haptics, IntroTempo(cover = 1.0, sections = 1.0), now = { base })

    @Test
    fun `a card waits hidden, plays once shown on an active page, and lands at its end`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        assertEquals(0f, clock.reveal(IntroSequence.Cover, base)(IntroKey.Header))
        clock.isActive = true
        assertEquals(IntroClock.Phase.Playing(start), clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        // The haptics start with the frames.
        assertEquals(listOf(start), spy.starts)
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        // A key reads 0 until its beat, while its sequence plays.
        assertEquals(0f, clock.reveal(IntroSequence.Cover, after(0.1))(IntroKey.Row(0)))
        val reveal = clock.reveal(IntroSequence.Cover, after(0.46))
        assertEquals(1f, reveal(IntroKey.Header))
        assertEquals(0.5f, reveal(IntroKey.Row(0)), 1e-6f)
        // A key the beats don't name has landed.
        assertEquals(1f, reveal(IntroKey.Row(1)))
        clock.advance(after(0.5))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(after(0.7))
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
    }

    @Test
    fun `the deck's top card plays, and each card shown in turn plays in its turn`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        listOf(IntroSequence.Cover, weeks, persons).forEach { clock.register(it) { beats } }
        clock.isActive = true
        // Registered, a card waits until it is on top.
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        clock.show(IntroSequence.Cover)
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(at(1.0))
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        // The card beneath never plays on its own.
        assertEquals(IntroClock.Phase.Waiting, clock.phase(weeks))
        clock.show(weeks)
        assertTrue(clock.isPlaying(weeks))
        clock.advance(at(2.0))
        clock.show(persons)
        assertEquals(listOf(IntroSequence.Cover, weeks, persons), spy.played)
    }

    @Test
    fun `a card thrown off mid-play lands and the next plays - one brought back stays landed`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        clock.show(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
        assertTrue(clock.isPlaying(weeks))
        // Swiping back down: the cover is settled for good, and the weeks land.
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertEquals(listOf(IntroSequence.Cover, weeks), spy.played)
        assertTrue(ledger.contains(october, weeks))
    }

    @Test
    fun `a card shown before its beats are registered plays as they arrive, with the latest`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        assertEquals(IntroClock.Phase.Waiting, clock.phase(IntroSequence.Cover))
        assertTrue(spy.played.isEmpty())
        val slow = listOf(IntroBeat(IntroKey.Header, 0.0, 2.0, IntroTick.light))
        clock.register(IntroSequence.Cover) { slow }
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(listOf(slow), spy.beats)
        // Registered again while it plays (the host reappeared): the score in play stands.
        clock.register(IntroSequence.Cover) { beats }
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        clock.advance(at(1.0))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `a card with no beats to play lands at once, silent`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.register(weeks) { emptyList() }
        clock.isActive = true
        clock.show(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertTrue(spy.played.isEmpty())
        assertEquals(1f, clock.reveal(weeks, base)(IntroKey.Header))
    }

    @Test
    fun `a card that leaves while it plays lands, and one that waits stays unrecorded`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(weeks)
        clock.leave(weeks)
        assertEquals(IntroClock.Phase.Settled, clock.phase(weeks))
        assertEquals(listOf(weeks), spy.stopped)
        clock.leave(persons)
        assertFalse(ledger.contains(october, persons))
    }

    @Test
    fun `a tap lands what plays and what waits on top, and counts them as played`() {
        val ledger = IntroLedger()
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.skip()
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertTrue(ledger.contains(october, IntroSequence.Cover))
        assertTrue(spy.played.isEmpty())
        val playing = clock(IntroLedger(), spy)
        playing.register(weeks) { beats }
        playing.isActive = true
        playing.show(weeks)
        playing.skip()
        assertEquals(IntroClock.Phase.Settled, playing.phase(weeks))
        assertEquals(listOf(weeks), spy.stopped)
    }

    @Test
    fun `a page stops only its own haptics, never another page's`() {
        val spy = HapticsSpy()
        val september = clock(haptics = spy, month = YearMonth.of(2026, 9))
        val clock = clock(haptics = spy)
        listOf(clock, september).forEach {
            it.register(IntroSequence.Cover) { beats }
            it.show(IntroSequence.Cover)
            it.isActive = true
        }
        clock.skip()
        assertEquals(listOf(october), spy.stoppedMonths)
        assertTrue(september.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `losing focus lands what plays, and it never plays again this launch`() {
        val ledger = IntroLedger()
        val clock = clock(ledger)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        clock.isActive = false
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        clock.isActive = true
        assertFalse(clock.isPlaying(IntroSequence.Cover))
    }

    @Test
    fun `a card shown on an inactive page waits, unrecorded, and plays on the next focus`() {
        val ledger = IntroLedger()
        val clock = clock(ledger)
        clock.register(weeks) { beats }
        clock.show(weeks)
        assertFalse(ledger.contains(october, weeks))
        clock.isActive = true
        assertTrue(clock.isPlaying(weeks))
    }

    @Test
    fun `a sequence played earlier this launch draws settled and never plays again`() {
        val ledger = IntroLedger()
        ledger.record(october, IntroSequence.Cover)
        val spy = HapticsSpy()
        val clock = clock(ledger, spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        assertTrue(spy.played.isEmpty())
        assertEquals(1f, clock.reveal(IntroSequence.Cover, base)(IntroKey.Header))
    }

    @Test
    fun `a sequence plays the beats its closure gives as it starts`() {
        var current = beats
        val clock = clock()
        clock.register(IntroSequence.Cover) { current }
        current = listOf(IntroBeat(IntroKey.Header, 0.0, 2.0, IntroTick.light))
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        clock.advance(at(1.0))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(0.5f, clock.reveal(IntroSequence.Cover, at(1.05))(IntroKey.Header), 1e-6f)
    }

    @Test
    fun `a frozen clock draws its sequence at its instant and ignores the deck`() {
        val clock = IntroClock.frozen(october, IntroSequence.Cover, beats, elapsed = 0.46)
        assertEquals(0.5f, clock.reveal(IntroSequence.Cover)(IntroKey.Row(0)), 1e-6f)
        clock.register(weeks) { beats }
        clock.show(weeks)
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        assertEquals(IntroClock.Phase.Waiting, clock.phase(weeks))
    }

    @Test
    fun `a card playing its intro ticks its own arrival, a settled card gets the same tick, an idle page none`() {
        val spy = HapticsSpy()
        val clock = clock(haptics = spy)
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.isActive = true
        clock.show(IntroSequence.Cover)
        clock.landed(IntroSequence.Cover)
        // Playing: its own arrival ticks, not a second one.
        assertTrue(spy.ticks.isEmpty())
        clock.advance(at(1.0))
        clock.show(weeks)
        clock.advance(at(2.0))
        // Back on the cover, already played: the landing ticks.
        clock.show(IntroSequence.Cover)
        clock.landed(IntroSequence.Cover)
        assertEquals(listOf(IntroTick.arrive), spy.ticks)
        // A page that does not play stays silent.
        clock.isActive = false
        clock.landed(weeks)
        assertEquals(listOf(IntroTick.arrive), spy.ticks)
    }
}
