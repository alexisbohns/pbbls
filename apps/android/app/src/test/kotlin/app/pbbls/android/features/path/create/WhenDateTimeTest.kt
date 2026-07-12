package app.pbbls.android.features.path.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Guards the pure [WhenDateTime] conversions that back the two-step `WhenRow`
 * picker (D15): the wall-clock date must survive the round-trip through the
 * `DatePicker`'s UTC-midnight millis regardless of the device zone, and
 * [WhenDateTime.combine] must re-derive a DST-correct offset from the zone. Pure
 * JVM — no Android runtime.
 */
class WhenDateTimeTest {
    private fun utcMidnightMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `combine round-trips a UTC instant through toUtcDateMillis`() {
        val zone = ZoneId.of("UTC")
        val original = OffsetDateTime.parse("2026-07-08T23:30:00Z")

        val millis = WhenDateTime.toUtcDateMillis(original, zone)
        val restored = WhenDateTime.combine(millis, original.hour, original.minute, zone)

        assertEquals(original, restored)
    }

    @Test
    fun `round-trips a wall-clock date and time in a non-UTC, no-DST zone`() {
        val zone = ZoneId.of("Asia/Kolkata") // +05:30, no daylight saving
        val original = OffsetDateTime.parse("2026-03-15T21:45:00+05:30")
        val local = original.atZoneSameInstant(zone)

        val millis = WhenDateTime.toUtcDateMillis(original, zone)
        val restored = WhenDateTime.combine(millis, local.hour, local.minute, zone)

        assertEquals(original, restored)
    }

    @Test
    fun `toUtcDateMillis uses the wall-clock date in the zone, not the UTC date`() {
        val zone = ZoneId.of("America/New_York")
        // 03:00Z on the 8th is 23:00 on the 7th in New York (EDT, -04:00).
        val instant = OffsetDateTime.parse("2026-07-08T03:00:00Z")

        val millis = WhenDateTime.toUtcDateMillis(instant, zone)

        assertEquals(utcMidnightMillis(LocalDate.of(2026, 7, 7)), millis)
        assertNotEquals(utcMidnightMillis(LocalDate.of(2026, 7, 8)), millis)
    }

    @Test
    fun `combine attaches the wall-clock time to the picked date in the zone`() {
        val zone = ZoneId.of("America/New_York")
        val the7th = utcMidnightMillis(LocalDate.of(2026, 7, 7))

        val combined = WhenDateTime.combine(the7th, 23, 30, zone)

        assertEquals(OffsetDateTime.parse("2026-07-07T23:30:00-04:00"), combined)
    }

    @Test
    fun `combine derives the DST-correct offset from the zone`() {
        val zone = ZoneId.of("America/New_York")
        val winter = WhenDateTime.combine(utcMidnightMillis(LocalDate.of(2026, 1, 15)), 12, 0, zone)
        val summer = WhenDateTime.combine(utcMidnightMillis(LocalDate.of(2026, 7, 15)), 12, 0, zone)

        assertEquals(ZoneOffset.ofHours(-5), winter.offset)
        assertEquals(ZoneOffset.ofHours(-4), summer.offset)
        assertEquals(zone.rules.getOffset(winter.toInstant()), winter.offset)
        assertEquals(zone.rules.getOffset(summer.toInstant()), summer.offset)
    }

    @Test
    fun `formatRow is non-empty and joins date and time with the middot`() {
        val row =
            WhenDateTime.formatRow(
                OffsetDateTime.parse("2026-07-08T14:23:00Z"),
                ZoneId.of("UTC"),
                Locale.US,
            )

        assertTrue(row.isNotBlank())
        assertTrue(row, row.contains("·"))
    }
}
