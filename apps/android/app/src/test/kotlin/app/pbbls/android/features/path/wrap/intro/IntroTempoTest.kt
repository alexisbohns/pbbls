package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** iOS `IntroTempoTests.factors`, `.neverFaster`, `.scaled`, `.clockScales`, and `IntroScoreSignaturesTests.scaledSwell`. */
class IntroTempoTest {
    @Test
    fun `the cover and a section each have their factor`() {
        assertEquals(1.45, IntroTempo.standard.factor(IntroSequence.Cover), 0.0)
        assertEquals(2.5, IntroTempo.standard.factor(IntroSequence.Section(MonthSection.GEM)), 0.0)
    }

    @Test
    fun `a factor below 1 plays at the score's own pace`() {
        val tempo = IntroTempo(cover = 0.5, sections = 0.2)
        assertEquals(1.0, tempo.factor(IntroSequence.Cover), 0.0)
        assertEquals(1.0, tempo.factor(IntroSequence.Section(MonthSection.WEEKS)), 0.0)
    }

    @Test
    fun `a scaled beat starts and lands that many times later, with its key, tick and swell`() {
        val swell = IntroSwell(0.1f, 0.5f, 0.3f)
        val beat = IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light, swell).scaled(2.5)
        assertEquals(0.8, beat.at, 1e-12)
        assertEquals(0.7, beat.duration, 1e-12)
        assertEquals(IntroKey.Row(0), beat.key)
        assertEquals(IntroTick.light, beat.tick)
        assertEquals(swell, beat.swell)
    }

    @Test
    fun `the clock plays the cover and a section at their tempo, frames and haptics alike`() {
        val base = 1_000_000_000_000L
        val beats =
            listOf(
                IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light),
                IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light),
            )
        val weeks = IntroSequence.Section(MonthSection.WEEKS)
        val spy = HapticsSpy()
        val clock = IntroClock(YearMonth.of(2026, 10), IntroLedger(), spy, IntroTempo(cover = 2.0, sections = 3.0), now = { base })
        clock.register(IntroSequence.Cover) { beats }
        clock.register(weeks) { beats }
        clock.show(IntroSequence.Cover)
        clock.isActive = true
        val start = base + IntroClock.LEAD_NANOS

        fun after(s: Double) = start + (s * 1e9).toLong()
        // 0.14 s into row 0's beat, which now starts at 0.64 s and takes 0.56 s.
        assertEquals(0.25f, clock.reveal(IntroSequence.Cover, after(0.32 * 2 + 0.14))(IntroKey.Row(0)), 1e-6f)
        assertEquals(listOf(beats.map { it.scaled(2.0) }), spy.beats)
        // The cover lands at its scaled end, 1.2 s, and the weeks, shown next, play three times slower.
        clock.advance(after(1.1))
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        clock.advance(after(1.2))
        assertFalse(clock.isPlaying(IntroSequence.Cover))
        clock.show(weeks)
        assertTrue(clock.isPlaying(weeks))
        assertEquals(beats.map { it.scaled(3.0) }, spy.beats.last())
    }
}
