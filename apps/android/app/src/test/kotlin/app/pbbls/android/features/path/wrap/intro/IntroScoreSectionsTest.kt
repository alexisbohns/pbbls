package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `IntroScoreSectionsTests.arrival` and `IntroScoreInsightsTests.insights`' head. */
class IntroScoreSectionsTest {
    @Test
    fun `a section's head is drawn from the start, its beat the arrival's tick alone`() {
        val header = IntroScore.head(hasLead = false, insights = false).first { it.key == IntroKey.Header }
        assertEquals(0.0, header.at, 0.0)
        assertEquals(0.0, header.duration, 0.0)
        assertEquals(IntroTick.arrive, header.tick)
    }

    @Test
    fun `leading the insights, their head ticks the arrival and the section's own head is silent, later`() {
        val beats = IntroScore.head(hasLead = true, insights = true)
        assertEquals(IntroKey.Insights, beats.first().key)
        assertEquals(IntroTick.arrive, beats.first().tick)
        val header = beats.first { it.key == IntroKey.Header }
        assertEquals(IntroScore.INSIGHTS_SHIFT, header.at, 0.0)
        assertNull(header.tick)
        assertEquals(IntroScore.INSIGHTS_SHIFT + IntroScore.LEAD_AT, beats.first { it.key == IntroKey.Lead }.at, 1e-12)
    }

    @Test
    fun `the rest of a section shifts only under the insights head`() {
        assertEquals(0.0, IntroScore.shift(insights = false), 0.0)
        assertEquals(IntroScore.INSIGHTS_SHIFT, IntroScore.shift(insights = true), 0.0)
    }
}
