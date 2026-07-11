package app.pbbls.android.features.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

class PathRowFormattingTest {
    private val happenedAt = OffsetDateTime.parse("2026-07-06T15:42:00+00:00")

    @Test
    fun `formats english weekday and localized short time`() {
        val line = PathRowFormatting.weekdayTime(happenedAt, ZoneOffset.UTC, Locale.ENGLISH)
        // JVM CLDR data may render "3:42 PM" with a narrow no-break space.
        assertTrue(line, line.startsWith("Monday · 3:42"))
        assertTrue(line, line.contains("PM"))
    }

    @Test
    fun `formats french weekday and 24h time`() {
        assertEquals(
            "lundi · 15:42",
            PathRowFormatting.weekdayTime(happenedAt, ZoneOffset.UTC, Locale.FRENCH),
        )
    }

    @Test
    fun `converts into the supplied zone before formatting`() {
        val line =
            PathRowFormatting.weekdayTime(
                happenedAt,
                ZoneOffset.ofHours(9),
                Locale.FRENCH,
            )
        // 15:42 UTC is 00:42 the next day at +09:00.
        assertEquals("mardi · 00:42", line)
    }
}
