package app.pbbls.android.features.path

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class WeekRollBuilderTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun pebble(
        happenedAt: String,
        name: String = "p",
    ): Pebble =
        Pebble(
            id = name,
            name = name,
            happenedAt = OffsetDateTime.parse(happenedAt),
            createdAt = OffsetDateTime.parse(happenedAt),
            intensity = 2,
            positiveness = 0,
        )

    private fun weekStarts(entries: List<WeekRollEntry>): List<LocalDate> = entries.map { it.weekStart }

    // ISO-year boundaries

    @Test
    fun `days around new year bucket into the 2025-12-29 monday - week 1 of week-based-year 2026`() {
        val entries =
            WeekRollBuilder.build(
                pebbles =
                    listOf(
                        pebble("2025-12-29T10:00:00+00:00"),
                        pebble("2026-01-01T10:00:00+00:00"),
                        pebble("2026-01-04T10:00:00+00:00"),
                    ),
                zone = utc,
                today = LocalDate.of(2026, 1, 2),
            )

        assertEquals(listOf(LocalDate.of(2025, 12, 29)), weekStarts(entries))
        assertEquals(3, entries.single().pebbles.size)
        assertEquals(1, WeekRollBuilder.isoWeekNumber(entries.single().weekStart))
    }

    @Test
    fun `2026 ends on a week 53`() {
        // 2026-12-28 is a Monday; its ISO week is week 53 of week-based-year 2026.
        val start = WeekRollBuilder.weekStart(LocalDate.of(2027, 1, 3))
        assertEquals(LocalDate.of(2026, 12, 28), start)
        assertEquals(53, WeekRollBuilder.isoWeekNumber(start))
    }

    @Test
    fun `sunday belongs to the prior monday's week`() {
        assertEquals(
            LocalDate.of(2026, 7, 6),
            WeekRollBuilder.weekStart(LocalDate.of(2026, 7, 12)),
        )
    }

    // Union + no empty future weeks

    @Test
    fun `empty input still yields the current week`() {
        val entries = WeekRollBuilder.build(emptyList(), utc, LocalDate.of(2026, 7, 11))

        assertEquals(listOf(LocalDate.of(2026, 7, 6)), weekStarts(entries))
        assertTrue(entries.single().pebbles.isEmpty())
    }

    @Test
    fun `weeks between pebbles are not fabricated`() {
        val entries =
            WeekRollBuilder.build(
                pebbles =
                    listOf(
                        pebble("2026-06-01T10:00:00+00:00"),
                        pebble("2026-06-22T10:00:00+00:00"),
                    ),
                zone = utc,
                today = LocalDate.of(2026, 7, 11),
            )

        // Two populated weeks + the current week — no empty gaps in between.
        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 7, 6),
            ),
            weekStarts(entries),
        )
    }

    @Test
    fun `future weeks appear only when a pebble is dated into them`() {
        val entries =
            WeekRollBuilder.build(
                pebbles = listOf(pebble("2026-07-20T10:00:00+00:00")),
                zone = utc,
                today = LocalDate.of(2026, 7, 11),
            )

        assertEquals(
            listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 20)),
            weekStarts(entries),
        )
    }

    // Asymmetric per-week sort

    @Test
    fun `past weeks sort ascending - current and future descending`() {
        val today = LocalDate.of(2026, 7, 11)
        val entries =
            WeekRollBuilder.build(
                pebbles =
                    listOf(
                        // Past week (June 1)
                        pebble("2026-06-03T10:00:00+00:00", name = "past-later"),
                        pebble("2026-06-01T10:00:00+00:00", name = "past-earlier"),
                        // Current week (July 6)
                        pebble("2026-07-07T10:00:00+00:00", name = "current-earlier"),
                        pebble("2026-07-10T10:00:00+00:00", name = "current-later"),
                        // Future week (July 20)
                        pebble("2026-07-20T10:00:00+00:00", name = "future-earlier"),
                        pebble("2026-07-22T10:00:00+00:00", name = "future-later"),
                    ),
                zone = utc,
                today = today,
            )

        val byWeek = entries.associateBy { it.weekStart }
        assertEquals(
            listOf("past-earlier", "past-later"),
            byWeek[LocalDate.of(2026, 6, 1)]?.pebbles?.map { it.name },
        )
        assertEquals(
            listOf("current-later", "current-earlier"),
            byWeek[LocalDate.of(2026, 7, 6)]?.pebbles?.map { it.name },
        )
        assertEquals(
            listOf("future-later", "future-earlier"),
            byWeek[LocalDate.of(2026, 7, 20)]?.pebbles?.map { it.name },
        )
    }

    // Zone sensitivity

    @Test
    fun `bucketing follows the supplied zone`() {
        // Sunday 23:30 UTC is already Monday 08:30 in Tokyo — a different week.
        val pebbles = listOf(pebble("2026-07-12T23:30:00+00:00"))

        val utcEntries = WeekRollBuilder.build(pebbles, utc, LocalDate.of(2026, 7, 12))
        val tokyoEntries =
            WeekRollBuilder.build(pebbles, ZoneId.of("Asia/Tokyo"), LocalDate.of(2026, 7, 13))

        assertEquals(LocalDate.of(2026, 7, 6), utcEntries.first().weekStart)
        assertEquals(LocalDate.of(2026, 7, 13), tokyoEntries.first().weekStart)
    }

    // previous / next

    @Test
    fun `previous and next step entries and return null at the edges`() {
        val entries =
            WeekRollBuilder.build(
                pebbles =
                    listOf(
                        pebble("2026-06-01T10:00:00+00:00"),
                        pebble("2026-06-22T10:00:00+00:00"),
                    ),
                zone = utc,
                today = LocalDate.of(2026, 7, 11),
            )

        val first = LocalDate.of(2026, 6, 1)
        val middle = LocalDate.of(2026, 6, 22)
        val last = LocalDate.of(2026, 7, 6)

        assertNull(WeekRollBuilder.previous(first, entries))
        assertEquals(first, WeekRollBuilder.previous(middle, entries)?.weekStart)
        assertEquals(last, WeekRollBuilder.next(middle, entries)?.weekStart)
        assertNull(WeekRollBuilder.next(last, entries))
        // Unknown week — no stepping.
        assertNull(WeekRollBuilder.previous(LocalDate.of(2020, 1, 6), entries))
    }
}
