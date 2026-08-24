package app.pbbls.android.features.pebblemedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Mirrors iOS `ExifCaptureDateTests`. Only the pure parse is covered — reading
 * the stream needs a `Context` and an image fixture, and the format handling is
 * the part with edge cases.
 *
 * Every null path matters: the caller falls back to *now* on each of them, never
 * to a date we guessed.
 */
class ExifCaptureDateTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun parsesADateTimeOriginalAsAWallClockInTheGivenZone() {
        val parsed = ExifCaptureDate.parse("2026:08:23 14:05:09", paris)
        // 14:05 in Paris in August is UTC+2.
        assertEquals(2026, parsed?.year)
        assertEquals(8, parsed?.monthValue)
        assertEquals(23, parsed?.dayOfMonth)
        assertEquals(14, parsed?.hour)
        assertEquals(5, parsed?.minute)
        assertEquals(9, parsed?.second)
        assertEquals(ZoneOffset.ofHours(2), parsed?.offset)
    }

    @Test
    fun parsesInUtcWhenTheZoneIsUtc() {
        val parsed = ExifCaptureDate.parse("2026:01:02 03:04:05", ZoneOffset.UTC)
        assertEquals(ZoneOffset.UTC, parsed?.offset)
        assertEquals(3, parsed?.hour)
    }

    @Test
    fun tolerantOfSurroundingWhitespace() {
        assertEquals(
            ExifCaptureDate.parse("2026:08:23 14:05:09", ZoneOffset.UTC),
            ExifCaptureDate.parse("  2026:08:23 14:05:09 ", ZoneOffset.UTC),
        )
    }

    @Test
    fun nullForAnAbsentValue() {
        assertNull(ExifCaptureDate.parse(null, ZoneOffset.UTC))
    }

    @Test
    fun nullForABlankValue() {
        assertNull(ExifCaptureDate.parse("", ZoneOffset.UTC))
        assertNull(ExifCaptureDate.parse("   ", ZoneOffset.UTC))
    }

    @Test
    fun nullForAMalformedValue() {
        assertNull(ExifCaptureDate.parse("not a date", ZoneOffset.UTC))
        // ISO-8601, not the EXIF format — a plausible near-miss.
        assertNull(ExifCaptureDate.parse("2026-08-23T14:05:09", ZoneOffset.UTC))
        assertNull(ExifCaptureDate.parse("2026:08:23", ZoneOffset.UTC))
    }

    /** The all-zero placeholder some cameras write is not a date. */
    @Test
    fun nullForTheAllZeroPlaceholder() {
        assertNull(ExifCaptureDate.parse("0000:00:00 00:00:00", ZoneOffset.UTC))
    }

    @Test
    fun nullForAnOutOfRangeValue() {
        assertNull(ExifCaptureDate.parse("2026:13:01 00:00:00", ZoneOffset.UTC))
        assertNull(ExifCaptureDate.parse("2026:02:30 00:00:00", ZoneOffset.UTC))
    }
}
