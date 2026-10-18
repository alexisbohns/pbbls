package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathPebble
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.WeekRollBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The web's `spine-week.test.ts`, replayed. The web runs under Europe/Paris;
 * here UTC serves wherever the day does not depend on the zone, and Paris where
 * the web case leans on its wall clock (the DST Sunday).
 */
class SpineWeekBuilderTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val w41: LocalDate = LocalDate.of(2026, 10, 5)

    private fun moment(
        id: String,
        happenedAt: String,
        completed: Boolean = true,
        name: String? = id,
    ): PathMoment {
        val at = OffsetDateTime.parse(happenedAt)
        return PathMoment(
            id = id,
            name = name,
            happenedAt = at,
            createdAt = at,
            pebble = if (completed) PathPebble(id = "pebble-$id", intensity = 2, positiveness = 0) else null,
        )
    }

    /** A completed moment. */
    private fun pebble(
        id: String,
        happenedAt: String,
    ) = moment(id, happenedAt)

    /** A moment that is not a pebble yet. */
    private fun bare(
        id: String,
        happenedAt: String,
    ) = moment(id, happenedAt, completed = false)

    private fun entryOf(
        moments: List<PathMoment>,
        today: LocalDate,
        zone: ZoneId = utc,
        weekStart: LocalDate = w41,
    ): WeekRollEntry =
        WeekRollBuilder.build(moments, zone, today).firstOrNull { it.weekStart == weekStart }
            ?: error("no entry $weekStart")

    private fun weekOf(
        moments: List<PathMoment>,
        today: LocalDate,
        zone: ZoneId = utc,
        weekStart: LocalDate = w41,
    ): SpineWeek = SpineWeekBuilder.build(entryOf(moments, today, zone, weekStart), today, zone)

    // Deliberately out of order, two on Tuesday, whole-second and fractional.
    private val week =
        listOf(
            pebble("tue-late", "2026-10-06T19:00:00Z"),
            pebble("sat", "2026-10-10T09:30:00.250+00:00"),
            pebble("mon", "2026-10-05T08:15:00Z"),
            pebble("tue-early", "2026-10-06T06:45:00.123456+00:00"),
            pebble("sun", "2026-10-11T21:59:00Z"), // Sun 23:59 Paris
        )

    private val ids: (List<PathMoment>) -> List<String> = { list -> list.map { it.id } }

    @Test
    fun `lays a past week out as seven days, Monday first`() {
        val week = weekOf(week, LocalDate.of(2026, 10, 20))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), week.days.map { it.index })
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), week.days.map { it.date.dayOfMonth })
    }

    @Test
    fun `reads oldest first in a past week`() {
        val week = weekOf(week, LocalDate.of(2026, 10, 20))
        assertEquals(listOf("mon", "tue-early", "tue-late", "sat", "sun"), ids(week.readingOrder))
    }

    @Test
    fun `reads oldest first in the current week too, unlike the roll`() {
        val today = LocalDate.of(2026, 10, 11)
        val entry = entryOf(week, today)
        // The roll keeps its newest-first current week for its other consumers...
        assertEquals("sun", entry.moments.first().id)
        // ...and the Spine re-sorts it as a book.
        assertEquals(
            listOf("mon", "tue-early", "tue-late", "sat", "sun"),
            ids(SpineWeekBuilder.build(entry, today, utc).readingOrder),
        )
    }

    @Test
    fun `marks past days with no pebble still`() {
        val week = weekOf(week, LocalDate.of(2026, 10, 20))
        val still = week.days.filter { it.still }
        assertEquals(listOf(2, 3, 4), still.map { it.index })
        assertTrue(still.all { it.moments.isEmpty() })
    }

    @Test
    fun `omits the current week's future days, and keeps today`() {
        val today = LocalDate.of(2026, 10, 7) // Wednesday
        val week = weekOf(week.filter { it.id in setOf("mon", "tue-early") }, today)
        assertEquals(listOf(0, 1, 2), week.days.map { it.index })
        val wednesday = week.days[2]
        assertTrue(wednesday.isToday)
        assertTrue(wednesday.still)
        assertTrue(wednesday.moments.isEmpty())
        assertEquals(1, week.days.count { it.isToday })
    }

    @Test
    fun `never drops a future-dated pebble`() {
        val today = LocalDate.of(2026, 10, 7)
        val week = weekOf(listOf(pebble("fri", "2026-10-09T10:00:00Z")), today)
        assertEquals(listOf(0, 1, 2, 4), week.days.map { it.index })
        assertFalse(week.days.last().still)
    }

    @Test
    fun `keeps a Sunday 23-59 pebble on the Sunday the clocks go back`() {
        val sunday = pebble("dst", "2026-10-25T22:59:00Z") // Sun 25 Oct 23:59 Paris, UTC+1
        val week = weekOf(listOf(sunday), LocalDate.of(2026, 11, 2), paris, LocalDate.of(2026, 10, 19))
        assertEquals(listOf("dst"), ids(week.days[6].moments))
        assertEquals(listOf(19, 20, 21, 22, 23, 24, 25), week.days.map { it.date.dayOfMonth })
    }

    @Test
    fun `returns the input in chronological order when the days are read in order`() {
        // The successor of path-layout's round-robin invariant: whatever order the
        // pebbles arrive in, reading the Spine top to bottom is chronological.
        val shuffled = week.reversed()
        val read = weekOf(shuffled, LocalDate.of(2026, 10, 20)).readingOrder
        val chronological = week.sortedBy { it.happenedAt.toInstant() }
        assertEquals(chronological, read)
        assertEquals(41, WeekRollBuilder.isoWeekNumber(WeekRollBuilder.weekStart(read.first().happenedAt.toLocalDate())))
    }

    @Test
    fun `gives a bare moment its day - the day is not still`() {
        // Wednesday 7 Oct is still in the fixture week; a bare moment fills it.
        val week = weekOf(week + bare("wed-bare", "2026-10-07T12:00:00Z"), LocalDate.of(2026, 10, 20))
        val wednesday = week.days[2]
        assertFalse(wednesday.still)
        assertEquals(listOf("wed-bare"), ids(wednesday.moments))
        assertEquals(listOf(3, 4), week.days.filter { it.still }.map { it.index })
    }

    @Test
    fun `reads a bare moment in its place among the pebbles`() {
        val between = bare("tue-noon", "2026-10-06T10:00:00Z")
        val read = weekOf(week + between, LocalDate.of(2026, 10, 20)).readingOrder
        assertEquals(listOf("mon", "tue-early", "tue-noon", "tue-late", "sat", "sun"), ids(read))
    }

    @Test
    fun `counts only pebbles in a week of bare moments, and draws its days anyway`() {
        val today = LocalDate.of(2026, 10, 20)
        val moments = listOf(bare("mon", "2026-10-05T08:15:00Z"), bare("thu", "2026-10-08T18:00:00Z"))
        val entry = entryOf(moments, today)
        assertEquals(2, entry.moments.size)
        val week = SpineWeekBuilder.build(entry, today, utc)
        assertEquals(0, week.pebbleCount)
        assertEquals(listOf(0, 3), week.days.filter { !it.still }.map { it.index })
    }

    @Test
    fun `counts the completed moments of a mixed week`() {
        val week = weekOf(week + bare("wed-bare", "2026-10-07T12:00:00Z"), LocalDate.of(2026, 10, 20))
        assertEquals(6, week.readingOrder.size)
        assertEquals(5, week.pebbleCount)
    }

    @Test
    fun `lays out what iOS and Android wrote, verbatim`() {
        // The captured rows of `moments-captured.ts`, their timestamps verbatim:
        // Sunday 11 Oct 21:04 Paris (iOS photo-only, bare), then Monday 12 Oct:
        // 08:40 (Android bare, named), 10:30 (iOS completed), 20:05 (Android completed).
        val moments =
            listOf(
                moment("492238c2-e6c2-4cea-943b-26ea8e16b961", "2026-10-11T19:04:51+00:00", completed = false, name = null),
                moment("daa2a992-16d1-459a-90d0-e3d42fb863a9", "2026-10-12T08:30:00+00:00", name = "Coffee"),
                moment(
                    "ffd58f73-0339-4590-ad0e-84b894a266d8",
                    "2026-10-12T06:40:00+00:00",
                    completed = false,
                    name = "Morning coffee on the balcony",
                ),
                moment("a951736e-9a07-4605-bf13-e779d6f0d43a", "2026-10-12T18:05:00+00:00", name = "Evening walk with Maya"),
            )
        val today = LocalDate.of(2026, 10, 14)
        val w41 = weekOf(moments, today, paris)
        assertEquals(listOf<String?>(null), w41.readingOrder.map { it.name })
        assertEquals(0, w41.pebbleCount)
        assertEquals(1, w41.days[6].moments.size)

        val w42 = weekOf(moments, today, paris, LocalDate.of(2026, 10, 12))
        assertEquals(
            listOf("Morning coffee on the balcony", "Coffee", "Evening walk with Maya"),
            w42.days[0].moments.map { it.name },
        )
        assertEquals(2, w42.pebbleCount)
    }
}
