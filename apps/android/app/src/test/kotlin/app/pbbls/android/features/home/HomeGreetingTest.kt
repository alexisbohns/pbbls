package app.pbbls.android.features.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.Locale

/**
 * The greeting over the real `strings.xml` (#1383; the iOS `HomeGreetingTests` cases): the hour bands,
 * the Monday-morning and Sunday-evening overrides, the nameless title and the dateline in both languages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HomeGreetingTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources

    private fun lines(
        at: String,
        name: String = "Alexis",
        locale: Locale = Locale.ENGLISH,
    ) = HomeGreeting.lines(LocalDateTime.parse(at), name, resources, locale)

    @Test
    fun `the bands split at noon and six`() {
        assertEquals(HomeGreeting.Band.MORNING, HomeGreeting.band(0))
        assertEquals(HomeGreeting.Band.MORNING, HomeGreeting.band(11))
        assertEquals(HomeGreeting.Band.AFTERNOON, HomeGreeting.band(12))
        assertEquals(HomeGreeting.Band.AFTERNOON, HomeGreeting.band(17))
        assertEquals(HomeGreeting.Band.EVENING, HomeGreeting.band(18))
        assertEquals(HomeGreeting.Band.EVENING, HomeGreeting.band(23))
    }

    @Test
    fun `monday morning opens the week`() {
        val g = lines("2026-11-02T08:10")
        assertEquals("Morning, Alexis", g.title)
        assertEquals("Hope you'll have a pleasant week!", g.line)
        assertEquals("Monday 2 November", g.dateline)
    }

    @Test
    fun `sunday evening asks about the week`() {
        val g = lines("2026-11-08T20:45")
        assertEquals("Good evening, Alexis", g.title)
        assertEquals("How was your week?", g.line)
    }

    @Test
    fun `the bands fall back to their defaults`() {
        val afternoon = lines("2026-11-04T15:30")
        assertEquals("Good afternoon, Alexis", afternoon.title)
        assertEquals("Halfway through the day. Anything worth keeping?", afternoon.line)
        assertEquals("What stays with you from today?", lines("2026-11-04T19:00").line)
        assertEquals("A new day to keep something from.", lines("2026-11-04T11:59").line)
    }

    @Test
    fun `monday evening and sunday morning are ordinary`() {
        assertEquals("What stays with you from today?", lines("2026-11-02T20:00").line)
        assertEquals("A new day to keep something from.", lines("2026-11-08T09:00").line)
    }

    @Test
    fun `an empty or blank name drops the comma`() {
        assertEquals("Morning", lines("2026-11-04T09:00", name = "").title)
        assertEquals("Morning", lines("2026-11-04T09:00", name = "  \n ").title)
    }

    @Test
    fun `a name is trimmed`() {
        assertEquals("Morning, Alexis", lines("2026-11-04T09:00", name = " Alexis ").title)
    }

    @Test
    fun `the dateline names the weekday, the day and the month`() {
        assertEquals("Saturday 31 October", lines("2026-10-31T21:00").dateline)
        assertEquals("samedi 31 octobre", lines("2026-10-31T21:00", locale = Locale.FRENCH).dateline)
    }

    @Test
    fun `TalkBack reads the title, the line, then the dateline`() {
        assertEquals("Good evening, Alexis. What stays with you from today? Saturday 31 October.", lines("2026-10-31T21:00").spoken)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `french has every line`() {
        val g = lines("2026-11-02T08:10", locale = Locale.FRENCH)
        assertEquals("Bonjour, Alexis", g.title)
        assertEquals("Belle semaine à toi !", g.line)
        assertEquals("lundi 2 novembre", g.dateline)
        assertEquals("Bonsoir", lines("2026-11-08T20:45", name = "", locale = Locale.FRENCH).title)
        assertEquals("Comment s'est passée ta semaine ?", lines("2026-11-08T20:45", locale = Locale.FRENCH).line)
        assertEquals("Bon après-midi, Alexis", lines("2026-11-04T15:30", locale = Locale.FRENCH).title)
        assertEquals("La journée est à moitié écrite. Quelque chose à garder ?", lines("2026-11-04T15:30", locale = Locale.FRENCH).line)
        assertEquals("Qu'est-ce qui te reste de la journée ?", lines("2026-11-04T19:00", locale = Locale.FRENCH).line)
        assertEquals("Une nouvelle journée à garder.", lines("2026-11-04T09:00", locale = Locale.FRENCH).line)
    }
}
