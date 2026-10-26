package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.model.wrap.HourMark
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.Tone
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** iOS `MonthDeckTests.titles` and `MonthCardsTests.everyWeek`'s head. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class MonthHeadTest {
    private val copy = WrapCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `every section of the fixture shows, and the weeks head counts every week`() {
        MonthSection.entries.forEach { assertTrue("$it", MonthSections.isShown(it, wrap, lookups)) }
        assertEquals(
            copy.weeksHeader(wrap.weeks!!.size),
            MonthSections.head(MonthSection.WEEKS, wrap, lookups, copy, DayView.CIRCLE).label,
        )
    }

    @Test
    fun `the persons hint reads clockwise only around a ring`() {
        assertEquals(copy.clockwiseHint(), MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, DayView.CIRCLE).hint)
        assertNull(MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, DayView.CALENDAR).hint)
    }

    @Test
    fun `a section the server left out of the wrap does not show`() {
        val bare = wrap.copy(persons = null, gem = null, tone = null, hours = null, domains = null, milestones = null)
        MonthSection.entries
            .filter { it != MonthSection.WEEKS }
            .forEach { assertFalse("$it", MonthSections.isShown(it, bare, lookups)) }
    }

    @Test
    fun `a domain whose id does not resolve hides the domains, and so do known domains that all count zero`() {
        val known = wrap.domains!!.first { lookups.domain(it.domainId) != null }
        val unknown = known.copy(domainId = "domain-nope")
        assertFalse(MonthSections.isShown(MonthSection.DOMAINS, wrap.copy(domains = listOf(unknown)), lookups))
        assertFalse(MonthSections.isShown(MonthSection.DOMAINS, wrap.copy(domains = listOf(known.copy(count = 0), unknown)), lookups))
        assertTrue(MonthSections.isShown(MonthSection.DOMAINS, wrap.copy(domains = listOf(known.copy(count = 2), unknown)), lookups))
    }

    @Test
    fun `a tone without days hides the tone`() {
        assertFalse(MonthSections.isShown(MonthSection.TONE, wrap.copy(tone = wrap.tone!!.copy(days = emptyList())), lookups))
    }

    @Test
    fun `hours and milestones whose moments do not resolve are hidden`() {
        val hours = wrap.hours!!
        val lost = hours.items.map { HourMark("moment-nope", it.happened, it.collected) }
        assertFalse(MonthSections.isShown(MonthSection.HOURS, wrap.copy(hours = hours.copy(items = lost)), lookups))
        assertFalse(MonthSections.isShown(MonthSection.MILESTONES, wrap.copy(milestones = listOf("moment-nope")), lookups))
    }

    @Test
    fun `persons with no known soul still show, with no clockwise hint even on a ring`() {
        val strangers = wrap.copy(persons = wrap.persons!!.map { it.copy(soulId = "soul-nope") })
        assertTrue(MonthSections.isShown(MonthSection.PERSONS, strangers, lookups))
        assertTrue(MonthSections.namedPersons(strangers, lookups).isEmpty())
        assertNull(MonthSections.head(MonthSection.PERSONS, strangers, lookups, copy, DayView.CIRCLE).hint)
    }

    @Test
    fun `each head's label is the string its section drew before, and the insights head is its own`() {
        val label = { section: MonthSection -> MonthSections.head(section, wrap, lookups, copy).label }
        assertEquals(copy.milestonesHeader(MonthSections.milestones(wrap, lookups).size), label(MonthSection.MILESTONES))
        assertEquals(copy.tile(WeekTile.PERSONS), label(MonthSection.PERSONS))
        assertEquals(copy.tile(WeekTile.DOMAINS), label(MonthSection.DOMAINS))
        assertEquals(copy.tile(WeekTile.EMOTIONS), label(MonthSection.GEM))
        assertEquals(copy.toneTitle(), label(MonthSection.TONE))
        assertEquals(copy.hoursTitle(), label(MonthSection.HOURS))
        assertEquals(copy.weeksHint(wrap.month), MonthSections.head(MonthSection.WEEKS, wrap, lookups, copy).hint)
        assertEquals(copy.milestonesHint(), MonthSections.head(MonthSection.MILESTONES, wrap, lookups, copy).hint)
        assertEquals(copy.gemHint(), MonthSections.head(MonthSection.GEM, wrap, lookups, copy).hint)
        assertNull(MonthSections.head(MonthSection.DOMAINS, wrap, lookups, copy).hint)
        assertEquals(MonthHead(copy.insights(), copy.insightsHint()), MonthSections.insightsHead(copy))
    }

    @Test
    fun `the deck's insights head goes to the first of tone and hours that shows`() {
        val noTone = wrap.copy(tone = Tone(emptyList(), wrap.tone!!.verdict, null, null))
        assertTrue(MonthSections.leadsInsights(MonthSection.HOURS, noTone, lookups))
        assertFalse(MonthSections.leadsInsights(MonthSection.TONE, noTone, lookups))
    }
}
