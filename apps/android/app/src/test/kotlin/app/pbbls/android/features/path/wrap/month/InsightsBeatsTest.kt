package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.model.wrap.MonthHours
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `the deck's tone and hours cards play the figures' entrances, the insights head on the one that leads`() {
        val tone = MonthDeck.beats(IntroSequence.Section(MonthSection.TONE), october, lookups, leadsInsights = true)
        assertEquals(ToneFigure.introBeats(october, insights = true), tone)
        assertEquals(IntroKey.Insights, tone.first().key)
        val hours = MonthDeck.beats(IntroSequence.Section(MonthSection.HOURS), october, lookups, leadsInsights = false)
        assertEquals(HoursFigure.introBeats(october, lookups, insights = false), hours)
        assertFalse(hours.any { it.key == IntroKey.Insights })
    }

    @Test
    fun `each day's beat starts as the sweep reaches half a bar past that day`() {
        val days = requireNotNull(october.tone).days
        val beats = ToneFigure.introBeats(october, insights = false)
        val sweep = beats.first { it.key == IntroKey.Sweep }
        // A 390 dp card's figure at 3x: its width, its inset and its bar in px.
        val width = 1038f
        val inset = 30f
        val bar = 15.6f
        val step = (width - 2 * inset) / (days.size - 1)
        val reached = beats.mapNotNull { beat -> (beat.key as? IntroKey.Day)?.let { it.day to beat.at } }
        assertEquals(days.indices.filter { days[it].up + days[it].even + days[it].down > 0 }, reached.map { it.first })
        reached.forEach { (i, at) ->
            val eased = RevealMotion.easeInOut((at - sweep.at) / sweep.duration).toFloat()
            assertEquals("day $i", inset + i * step + bar / 2, ToneFigure.sweepWidth(eased, width, inset, bar), 1e-3f)
        }
    }

    @Test
    fun `the hours play one stone per mark the device knows`() {
        val known = requireNotNull(october.hours).items.count { lookups.moment(it.momentId) != null }
        val beats = HoursFigure.introBeats(october, lookups, insights = false)
        assertEquals((0 until known).map { IntroKey.Stone(it) }, beats.map { it.key }.filterIsInstance<IntroKey.Stone>())
    }
}
