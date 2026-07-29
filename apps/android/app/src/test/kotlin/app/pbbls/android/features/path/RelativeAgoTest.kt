package app.pbbls.android.features.path

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * [relativeAgo] renders a draft's last-saved stamp. Pure so it is JVM-testable
 * without a device — and deliberately unit-bearing rather than plural-bearing, so
 * no per-unit catalog entries are needed (the surrounding sentence is localized
 * by `drafts_saved_ago`).
 */
class RelativeAgoTest {
    private val now = OffsetDateTime.of(2026, 7, 30, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun ago(seconds: Long) = relativeAgo(now.minusSeconds(seconds), now)

    @Test
    fun `sub-minute reads in seconds`() {
        assertEquals("0s", ago(0))
        assertEquals("45s", ago(45))
    }

    @Test
    fun `crossing each boundary switches unit`() {
        assertEquals("1m", ago(60))
        assertEquals("59m", ago(3_599))
        assertEquals("1h", ago(3_600))
        assertEquals("23h", ago(86_399))
        assertEquals("1d", ago(86_400))
        assertEquals("6d", ago(604_799))
        assertEquals("1w", ago(604_800))
    }

    @Test
    fun `long-abandoned drafts stay in weeks rather than overflowing`() {
        assertEquals("52w", ago(604_800 * 52))
    }

    @Test
    fun `a clock that moved backwards clamps to zero instead of going negative`() {
        // Device clock changes are real; "-3s ago" would be worse than "0s ago".
        assertEquals("0s", relativeAgo(now.plusSeconds(3), now))
    }
}
