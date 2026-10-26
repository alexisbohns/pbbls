package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.MonthSection
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `IntroTempoTests.factors`, `.neverFaster`, `.scaled`, and `IntroScoreSignaturesTests.scaledSwell`. */
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
}
