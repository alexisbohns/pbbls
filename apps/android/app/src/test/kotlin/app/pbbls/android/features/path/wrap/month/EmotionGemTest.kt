package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.ui.gem.GemGeometry
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.gem
import app.pbbls.android.features.path.wrap.intro.gemRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `EmotionGem.introBeats`: the count, then the families in the lead's order, each with the facets the server gave it. */
class EmotionGemTest {
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups
    private val gem = requireNotNull(wrap.gem)

    @Test
    fun `the gem plays the month's pebbles, then its families in the lead's order`() {
        val runs = IntroScore.gemRuns(gem.lead?.order.orEmpty(), gem.facets)
        assertEquals(IntroScore.gem(runs, wrap.pebbles), EmotionGem.introBeats(wrap))
        assertEquals(listOf("joy", "peace", "pride", "anger", "sadness", "fear"), runs.map { it.slug })
    }

    @Test
    fun `every facet the server coloured lights once, with its own family`() {
        val runs = IntroScore.gemRuns(gem.lead?.order.orEmpty(), gem.facets)
        val lit = runs.flatMap { run -> run.facets.map { it to run.slug } }
        assertEquals(GemGeometry.allocated.filter { gem.facets[it] != null }.toSet(), lit.map { it.first }.toSet())
        assertEquals(lit.size, lit.map { it.first }.toSet().size)
        assertTrue(lit.all { (facet, slug) -> gem.facets[facet] == slug })
    }

    @Test
    fun `a wrap without a gem plays nothing`() {
        assertTrue(EmotionGem.introBeats(wrap.copy(gem = null)).isEmpty())
    }

    @Test
    fun `the deck's gem card plays the gem's entrance`() {
        val card = IntroSequence.Section(MonthSection.GEM)
        assertEquals(EmotionGem.introBeats(wrap), MonthDeck.beats(card, wrap, lookups, leadsInsights = false))
        assertTrue(MonthDeck.beats(card, wrap, lookups, leadsInsights = false).any { it.key == IntroKey.Count })
    }
}
