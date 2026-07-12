package app.pbbls.android.features.path

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Verifies [PebbleReadDateFormat.format] renders the iOS-parity
 * "weekday-abbrev, month-abbrev day, year · localized time" line and that
 * `atZoneSameInstant` moves the wall clock into the supplied zone. Parts are
 * asserted separately rather than as one exact string: CLDR on JDK 21 separates
 * the time and day-period with a narrow no-break space, the same caveat
 * [PathRowFormattingTest] documents.
 */
class PebbleReadDateFormatTest {
    private val happenedAt = OffsetDateTime.parse("2026-07-08T14:23:45+00:00")

    @Test
    fun `formats the UTC fixture in en-US`() {
        val line = PebbleReadDateFormat.format(happenedAt, ZoneOffset.UTC, Locale.US)

        assertTrue(line, line.startsWith("Wed, Jul 8, 2026"))
        assertTrue(line, line.contains("·"))
        assertTrue(line, line.contains("2:23"))
        assertTrue(line, line.contains("PM"))
    }

    @Test
    fun `shifts the wall clock into a positive-offset zone`() {
        // 14:23 UTC is 23:23 the same calendar day at +09:00 — the instant is
        // unchanged, only the rendered wall clock moves (2:23 PM -> 11:23 PM).
        val line = PebbleReadDateFormat.format(happenedAt, ZoneOffset.ofHours(9), Locale.US)

        assertTrue(line, line.startsWith("Wed, Jul 8, 2026"))
        assertTrue(line, line.contains("11:23"))
        assertTrue(line, line.contains("PM"))
    }
}
