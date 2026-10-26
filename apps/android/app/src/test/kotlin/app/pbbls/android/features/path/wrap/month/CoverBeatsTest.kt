package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.features.path.wrap.intro.IntroKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cover's beats come from what the cover draws (iOS `MonthCoverView.introBeats`). */
class CoverBeatsTest {
    private val lines = setOf(IntroKey.Summary, IntroKey.Names, IntroKey.Colophon)

    @Test
    fun `the fixture's cover beats every day, its leaf, its souls in the ring and the lines it draws`() {
        val wrap = SpineFixtureWeek.monthWrap
        val lookups = SpineFixtureWeek.lookups
        val beats = coverBeats(wrap, lookups)
        assertEquals(31, beats.count { it.key is IntroKey.Slot })
        assertTrue(beats.any { it.key == IntroKey.Leaf })
        // The ring draws the souls the lookups resolve, at most eight: the fixture resolves all fourteen.
        val resolved = wrap.cover!!.soulsInOrder.count { lookups.soul(it) != null }
        assertEquals(14, resolved)
        assertEquals(minOf(resolved, 8), beats.count { it.key is IntroKey.Soul })
        assertEquals(listOf(IntroKey.Summary, IntroKey.Names, IntroKey.Colophon), beats.map { it.key }.filter { it in lines })
        assertEquals(wrap.numberedDays(), beats.mapNotNull { (it.key as? IntroKey.Numeral)?.day })
    }

    @Test
    fun `a cover with no souls, no inks and no known paper beats the summary alone`() {
        val wrap = SpineFixtureWeek.monthWrap
        val bare = wrap.copy(cover = wrap.cover!!.copy(soulsInOrder = emptyList(), inks = emptyList(), papers = listOf("domain-unknown")))
        val beats = coverBeats(bare, SpineFixtureWeek.lookups)
        assertEquals(listOf(IntroKey.Summary), beats.map { it.key }.filter { it in lines })
        assertTrue(beats.none { it.key is IntroKey.Soul })
    }

    @Test
    fun `an ink the lookups don't know still prints the colophon, as the cover names it by its slug`() {
        val wrap = SpineFixtureWeek.monthWrap
        val inked = wrap.copy(cover = wrap.cover!!.copy(inks = listOf("unknown-family"), papers = emptyList()))
        assertTrue(coverBeats(inked, SpineFixtureWeek.lookups).any { it.key == IntroKey.Colophon })
    }

    @Test
    fun `no cover, no beats`() {
        assertTrue(coverBeats(SpineFixtureWeek.monthWrap.copy(cover = null), SpineFixtureWeek.lookups).isEmpty())
    }
}
