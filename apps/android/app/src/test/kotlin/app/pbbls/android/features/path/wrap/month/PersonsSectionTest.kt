package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.persons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreSectionsTests.personsCap` and `MonthCardsTests.personsFill`. */
class PersonsSectionTest {
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `the persons section names five at most`() {
        val beats = PersonsSection.introBeats(wrap, lookups)
        assertTrue(beats.any { it.key == IntroKey.Glyph(0) })
        assertFalse(beats.any { it.key == IntroKey.Glyph(5) })
        assertTrue(beats.count { it.key is IntroKey.Glyph } <= 5)
    }

    @Test
    fun `the persons play over the souls the device names, in the server's order, their days as the rows draw them`() {
        val named = MonthSections.namedPersons(wrap, lookups)
        assertEquals(IntroScore.persons(named.take(5).map { it.days }, hasLead = false), PersonsSection.introBeats(wrap, lookups))
    }

    @Test
    fun `with nobody named, the persons play the invitation`() {
        val strangers = wrap.copy(persons = wrap.persons!!.map { it.copy(soulId = "soul-nope") })
        assertEquals(listOf(IntroKey.Header, IntroKey.Lead), PersonsSection.introBeats(strangers, lookups).map { it.key })
    }

    @Test
    fun `the deck's persons card plays the persons' entrance`() {
        val card = IntroSequence.Section(MonthSection.PERSONS)
        assertEquals(PersonsSection.introBeats(wrap, lookups), MonthDeck.beats(card, wrap, lookups, leadsInsights = false))
    }

    @Test
    fun `on a card the persons share the room left by more, their glyphs and rings growing, within bounds`() {
        val roomy = PersonsSection.rowSize(room = 500f, rows = 5)
        assertEquals((500f - PersonsSection.MORE_RESERVE) / 5, roomy.height, 1e-4f)
        assertTrue(roomy.glyph > SpineLayoutTokens.MARK.value && roomy.glyph <= PersonsSection.GLYPH_CAP)
        assertTrue(roomy.ring > ROW_RING.value && roomy.ring <= PersonsSection.RING_CAP)
        val tight = PersonsSection.rowSize(room = 200f, rows = 5)
        assertEquals(SpineLayoutTokens.ROW_MIN.value, tight.height, 0f)
        assertEquals(SpineLayoutTokens.MARK.value, tight.glyph, 0f)
        assertEquals(ROW_RING.value, tight.ring, 0f)
        assertEquals(PersonsSection.ROW_CAP, PersonsSection.rowSize(room = 2000f, rows = 2).height, 0f)
    }
}
