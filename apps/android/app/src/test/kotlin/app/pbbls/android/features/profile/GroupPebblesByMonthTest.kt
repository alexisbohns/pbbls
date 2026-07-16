package app.pbbls.android.features.profile

import app.pbbls.android.features.path.models.Pebble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * [groupPebblesByMonth] — mirrors iOS `GroupPebblesByMonthTests`. A fixed UTC
 * zone keeps the bucketing deterministic regardless of the machine running it.
 */
class GroupPebblesByMonthTest {
    private val utc = ZoneId.of("UTC")

    private fun pebble(happened: String): Pebble {
        val instant = OffsetDateTime.parse(happened)
        return Pebble(
            id = happened,
            name = "p",
            happenedAt = instant,
            createdAt = instant,
            intensity = 1,
            positiveness = 0,
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertTrue(groupPebblesByMonth(emptyList(), utc).isEmpty())
    }

    @Test
    fun `pebbles in the same month group together`() {
        val early = pebble("2026-04-02T10:00:00Z")
        val late = pebble("2026-04-28T22:00:00Z")
        val result = groupPebblesByMonth(listOf(early, late), utc)
        assertEquals(1, result.size)
        assertEquals(2, result[0].second.size)
    }

    @Test
    fun `different months produce separate groups ordered descending`() {
        val april = pebble("2026-04-02T10:00:00Z")
        val march = pebble("2026-03-15T10:00:00Z")
        val may = pebble("2026-05-01T10:00:00Z")
        val result = groupPebblesByMonth(listOf(may, april, march), utc)
        assertEquals(
            listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 4), YearMonth.of(2026, 3)),
            result.map { it.first },
        )
    }

    @Test
    fun `input order within a group is preserved`() {
        val first = pebble("2026-04-28T10:00:00Z")
        val second = pebble("2026-04-10T10:00:00Z")
        val result = groupPebblesByMonth(listOf(first, second), utc)
        assertEquals(1, result.size)
        assertEquals(first.happenedAt, result[0].second[0].happenedAt)
        assertEquals(second.happenedAt, result[0].second[1].happenedAt)
    }

    @Test
    fun `bucketing follows the provided zone, not the offset`() {
        // 2026-05-01T00:30+02:00 is still April 30 in UTC.
        val pebble = pebble("2026-05-01T00:30:00+02:00")
        val result = groupPebblesByMonth(listOf(pebble), utc)
        assertEquals(YearMonth.of(2026, 4), result[0].first)
    }
}
