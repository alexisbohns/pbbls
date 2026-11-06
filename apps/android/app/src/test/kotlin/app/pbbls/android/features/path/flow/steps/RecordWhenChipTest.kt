package app.pbbls.android.features.path.flow.steps

import android.app.Application
import app.pbbls.android.features.path.capture.WhenSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The chip's label with a fixed clock and zone. 2026-10-14 is a Wednesday (checked with `date`), so
 * with today the 15th it is yesterday; the 13th is a Tuesday. Under Robolectric only for the platform's
 * own weekday pattern, which the plain JVM's android.jar stubs do not have.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecordWhenChipTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 15)
    private val english = Locale.ENGLISH

    private fun label(
        at: String,
        source: WhenSource = WhenSource.NOW,
        locale: Locale = english,
    ) = whenLabel(
        happenedAt = OffsetDateTime.parse(at),
        today = today,
        zone = zone,
        locale = locale,
        source = source,
        todayLabel = "Today",
        yesterdayLabel = "Yesterday",
        fromPhoto = { "From your photo · $it" },
        weekdayAndDate = { it.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)) },
    )

    @Test
    fun `today keeps the time and says Today`() {
        assertTrue(label("2026-10-15T09:10:00+02:00").startsWith("Today, "))
        assertTrue(label("2026-10-15T09:10:00+02:00").contains("9:10"))
    }

    @Test
    fun `the day is the one in the zone, not in UTC`() {
        // 23:30 on the 14th in Paris is 21:30Z: still the 14th there, so yesterday.
        assertTrue(label("2026-10-14T21:30:00Z").startsWith("Yesterday, "))
        // 00:30 on the 15th in Paris is the 14th at 22:30Z: today, whatever UTC says.
        assertTrue(label("2026-10-14T22:30:00Z").startsWith("Today, "))
    }

    @Test
    fun `another day shows the weekday and the date`() {
        assertTrue(label("2026-10-13T10:30:00+02:00").startsWith("Tue, Oct 13, "))
        assertTrue(label("2026-10-11T10:30:00+02:00").startsWith("Sun, Oct 11, "))
    }

    @Test
    fun `a fixture's weekday is the real one`() {
        // The iOS plan once labelled a Friday a Wednesday: 2026-10-14 is a Wednesday.
        assertEquals("Wed, Oct 14", weekdayAndDate(ZonedDateTime.of(2026, 10, 14, 10, 30, 0, 0, zone), english))
    }

    @Test
    fun `from a photo wraps the whole label`() {
        val l = label("2026-10-11T10:30:00+02:00", WhenSource.PHOTO)
        assertTrue(l, l.startsWith("From your photo · Sun, Oct 11, "))
    }

    @Test
    fun `an edited date is not wrapped`() {
        assertTrue(!label("2026-10-11T10:30:00+02:00", WhenSource.EDITED).startsWith("From"))
    }

    @Test
    fun `the time follows the locale`() {
        val fr = label("2026-10-13T10:30:00+02:00", locale = Locale.FRENCH)
        assertTrue(fr, fr.endsWith("10:30"))
        assertEquals("mar. 13 oct.", weekdayAndDate(ZonedDateTime.of(2026, 10, 13, 10, 30, 0, 0, zone), Locale.FRENCH))
    }
}
