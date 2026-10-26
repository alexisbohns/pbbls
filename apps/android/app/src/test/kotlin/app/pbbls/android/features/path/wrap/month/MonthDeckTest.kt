package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroTick
import app.pbbls.android.features.path.wrap.intro.head
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthDeckTests` (#1241 D11): the deck deals a card for the cover and each section that shows. */
class MonthDeckTest {
    private val october = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    private fun section(s: MonthSection) = IntroSequence.Section(s)

    @Test
    fun `the fixture deals the cover, then each section in the server's order, then the end card`() {
        val expected =
            listOf(
                IntroSequence.Cover,
                section(MonthSection.WEEKS),
                section(MonthSection.MILESTONES),
                section(MonthSection.PERSONS),
                section(MonthSection.GEM),
                section(MonthSection.DOMAINS),
                section(MonthSection.TONE),
                section(MonthSection.HOURS),
                IntroSequence.End,
            )
        assertEquals(expected, MonthDeck.cards(october, lookups))
    }

    @Test
    fun `a month with nothing to deal deals no end card either`() {
        val empty = october.copy(sections = emptyList(), cover = null)
        assertTrue(MonthDeck.cards(empty, lookups).isEmpty())
    }

    @Test
    fun `an emptied section deals no card`() {
        MonthSection.entries.forEach { emptied ->
            val cards = MonthDeck.cards(emptying(october, emptied), lookups)
            assertFalse("$emptied", section(emptied) in cards)
            assertEquals("$emptied", 8, cards.size)
        }
    }

    @Test
    fun `a wrap without a cover deals none`() {
        assertEquals(section(MonthSection.WEEKS), MonthDeck.cards(october.copy(cover = null), lookups).first())
    }

    @Test
    fun `the insights head sits on the first tone or hours card dealt`() {
        val full = MonthDeck.cards(october, lookups)
        assertTrue(MonthDeck.leadsInsights(section(MonthSection.TONE), full))
        assertFalse(MonthDeck.leadsInsights(section(MonthSection.HOURS), full))
        assertFalse(MonthDeck.leadsInsights(section(MonthSection.GEM), full))
        // An empty tone deals no card: the head moves to the hours.
        val quiet = MonthDeck.cards(emptying(october, MonthSection.TONE), lookups)
        assertTrue(MonthDeck.leadsInsights(section(MonthSection.HOURS), quiet))
    }

    @Test
    fun `the deck and the sections agree on which card leads the insights`() {
        listOf(october, emptying(october, MonthSection.TONE), emptying(october, MonthSection.HOURS)).forEach { wrap ->
            val cards = MonthDeck.cards(wrap, lookups)
            wrap.sections.forEach {
                assertEquals("$it", MonthSections.leadsInsights(it, wrap, lookups), MonthDeck.leadsInsights(section(it), cards))
            }
        }
    }

    @Test
    fun `a section's card arrives on its head, the insights head when it leads, and the end card plays nothing yet`() {
        val tone = MonthDeck.beats(section(MonthSection.TONE), october, lookups, leadsInsights = true)
        assertEquals(IntroScore.head(hasLead = false, insights = true), tone)
        val gem = MonthDeck.beats(section(MonthSection.GEM), october, lookups, leadsInsights = false)
        assertEquals(listOf(IntroKey.Header), gem.map { it.key })
        assertEquals(IntroTick.arrive, gem.single().tick)
        assertEquals(coverBeats(october, lookups), MonthDeck.beats(IntroSequence.Cover, october, lookups, leadsInsights = false))
        assertTrue(MonthDeck.beats(IntroSequence.End, october, lookups, leadsInsights = false).isEmpty())
    }

    /** The wrap with [section] drawing nothing, as the server sends an empty one (iOS `emptying`). */
    private fun emptying(
        wrap: MonthWrap,
        section: MonthSection,
    ): MonthWrap =
        when (section) {
            MonthSection.WEEKS -> wrap.copy(weeks = emptyList())
            MonthSection.MILESTONES -> wrap.copy(milestones = emptyList())
            MonthSection.PERSONS -> wrap.copy(persons = null)
            MonthSection.DOMAINS -> wrap.copy(domains = emptyList())
            MonthSection.GEM -> wrap.copy(gem = null)
            MonthSection.TONE -> wrap.copy(tone = wrap.tone!!.copy(days = emptyList(), first = null, last = null))
            MonthSection.HOURS -> wrap.copy(hours = wrap.hours!!.copy(items = emptyList()))
        }
}
