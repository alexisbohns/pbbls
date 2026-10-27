package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.domains
import app.pbbls.android.features.path.wrap.intro.fillSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.domainsIntro` (#1241 D10). */
class DomainsSectionTest {
    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups

    @Test
    fun `the domains' intro fills each gauge to its share of the first, the fullest swelling most`() {
        val beats = DomainsSection.introBeats(wrap, lookups)
        val first = beats.first { it.key == IntroKey.Gauge(0) }
        val second = beats.first { it.key == IntroKey.Gauge(1) }
        assertEquals(IntroScore.fillSpan(1.0), first.duration, 0.0)
        assertTrue(first.duration > second.duration)
        assertTrue(first.swell!!.to > second.swell!!.to)
        assertTrue(beats.any { it.key == IntroKey.More })
    }

    @Test
    fun `the domains play the five rows the section draws, each its share of the fullest, more always`() {
        val known = wrap.domains!!.filter { lookups.domain(it.domainId) != null }
        val counts = known.take(5).map { it.count }
        val top = counts.max().toDouble()
        assertEquals(IntroScore.domains(counts.map { it / top }, more = true), DomainsSection.introBeats(wrap, lookups))
        assertFalse(DomainsSection.introBeats(wrap, lookups).any { it.key == IntroKey.Row(5) })
    }

    @Test
    fun `with no domain the device knows, the domains play their head alone`() {
        val strangers = wrap.copy(domains = wrap.domains!!.map { it.copy(domainId = "domain-nope") })
        assertEquals(IntroScore.domains(emptyList(), more = true), DomainsSection.introBeats(strangers, lookups))
        assertEquals(listOf(IntroKey.Header), DomainsSection.introBeats(strangers, lookups).map { it.key })
    }

    @Test
    fun `the deck's domains card plays the domains' gauges`() {
        val card = IntroSequence.Section(MonthSection.DOMAINS)
        assertEquals(DomainsSection.introBeats(wrap, lookups), MonthDeck.beats(card, wrap, lookups, leadsInsights = false))
    }
}
