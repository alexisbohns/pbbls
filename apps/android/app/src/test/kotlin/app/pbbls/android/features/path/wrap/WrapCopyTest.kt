package app.pbbls.android.features.path.wrap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The week's sentences over the real `strings.xml` (#1207, spec §9): English. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en")
class WrapCopyTest {
    private val copy = WrapCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)

    @Test
    fun `the line counts in words, says so far for the live week, and reads an empty week`() {
        assertEquals("Eleven pebbles, five people.", copy.line(11, 5, live = false, hasMoments = true))
        assertEquals("Thirteen pebbles, fourteen people so far.", copy.line(13, 14, live = true, hasMoments = true))
        assertEquals("One pebble.", copy.line(1, 0, live = false, hasMoments = true))
        assertEquals("No pebble yet.", copy.line(0, 0, live = true, hasMoments = true))
        assertEquals("Nothing yet.", copy.line(0, 0, live = true, hasMoments = false))
    }

    @Test
    fun `the leads arrange names the server ranked`() {
        assertEquals("Mostly joy, then peace.", copy.emotionsLead(listOf("Joy", "Peace", "Pride")))
        assertEquals("Mostly anger.", copy.emotionsLead(listOf("Anger")))
        assertEquals("Friends first, then identity and family.", copy.domainsLead(listOf("Friends", "Identity", "Family", "Travel")))
        assertEquals("Six bright, four even, three heavy.", copy.weightLead(6, 4, 3))
        // The JDK's short time puts a narrow no-break space before the day period.
        assertEquals("From 8:02\u202FAM to 10:30\u202FPM.", copy.hoursLead(8.04, 22.5))
    }

    @Test
    fun `the invitation names the week's own moments`() {
        assertEquals(
            "Was someone there for Flag march, Rent or Couldn’t sleep?",
            copy.invitation(listOf("Flag march", "Rent", "Couldn’t sleep"), long = false),
        )
        assertEquals(
            "Was someone there for Flag march? Name a soul once; it joins every moment you link.",
            copy.invitation(listOf("Flag march"), long = true),
        )
    }

    @Test
    fun `kept counts photos and cards`() {
        assertEquals("six photos", copy.photos(6))
        assertEquals("one card written", copy.cards(1))
        assertEquals("2 cards", copy.cardsShort(2))
    }

    @Test
    fun `the month's cover, sections and sheet read in English`() {
        val october = java.time.YearMonth.of(2026, 10)
        assertEquals("part the tenth", copy.monthKicker(october))
        assertEquals("October", copy.monthName(october))
        assertEquals(
            "Printed in joy, peace & pride, with anger, sadness & fear, on family, identity & friends.",
            copy.colophon(listOf("joy", "peace", "pride", "anger", "sadness", "fear"), listOf("family", "identity", "friends")),
        )
        assertEquals("five weeks", copy.weeksHeader(5))
        assertEquals("October by week", copy.weeksHint(october))
        assertEquals("one milestone", copy.milestonesHeader(1))
        assertEquals("all six persons", copy.morePersons(6))
        assertEquals("the persons, in detail", copy.morePersons(4))
        assertEquals("5–11 Oct", copy.range(october, 4, 10))
        assertEquals("Mon 5 Oct", copy.dateline(java.time.LocalDate.of(2026, 10, 5)))
        assertEquals("new since September", copy.newSince(october.minusMonths(1)))
        assertEquals(
            "Mara five times, Nadia once. Inès and Jules new since September.",
            copy.personsLead(listOf("Mara" to 5, "Nadia" to 1), listOf("Inès", "Jules"), october.minusMonths(1)),
        )
        assertEquals("Eleven emotions in six families, weighed by intensity.", copy.emotionsLead(11, 6))
        assertEquals("Seven domains; family in seven pebbles.", copy.domainsLead(7, "Family", 7))
    }
}
