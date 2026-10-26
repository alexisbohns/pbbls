package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthHours
import app.pbbls.android.features.path.wrap.intro.IntroKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroInsightsBeatsTests`: the insights head over a figure that draws nothing plays alone. */
class InsightsBeatsTest {
    private val october = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `hours with no known moments play nothing, or the insights head alone`() {
        val empty = october.copy(hours = MonthHours(emptyList(), null))
        assertTrue(HoursFigure.introBeats(empty, lookups, insights = false).isEmpty())
        assertEquals(listOf(IntroKey.Insights), HoursFigure.introBeats(empty, lookups, insights = true).map { it.key })
        val none = october.copy(hours = null)
        assertEquals(listOf(IntroKey.Insights), HoursFigure.introBeats(none, lookups, insights = true).map { it.key })
    }

    @Test
    fun `a tone that is null or has no days plays nothing, or the insights head alone`() {
        val tone = requireNotNull(october.tone)
        val noDays = october.copy(tone = tone.copy(days = emptyList()))
        val noTone = october.copy(tone = null)
        listOf(noDays, noTone).forEach { empty ->
            assertTrue(ToneFigure.introBeats(empty, insights = false).isEmpty())
            assertEquals(listOf(IntroKey.Insights), ToneFigure.introBeats(empty, insights = true).map { it.key })
        }
    }

    @Test
    fun `the fixture's own tone and hours still play their sweep and stones`() {
        listOf(false, true).forEach { insights ->
            assertTrue(ToneFigure.introBeats(october, insights).any { it.key == IntroKey.Sweep })
            assertTrue(HoursFigure.introBeats(october, lookups, insights).any { it.key == IntroKey.Dial })
        }
    }
}
