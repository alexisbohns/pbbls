package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PathPebble
import app.pbbls.android.core.model.PathSoul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The web's `persons-of-week.test.ts`, replayed under Europe/Paris as the web
 * runs: summer time ends Sunday 25 Oct 2026 at 03:00, so the week of Monday
 * 19 Oct has a 25-hour Sunday.
 */
class PersonsOfWeekTest {
    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val dstWeek: LocalDate = LocalDate.of(2026, 10, 19)
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    private fun soul(
        id: String,
        name: String = id,
    ) = PathSoul(id = id, name = name, glyphId = "g")

    /** A completed moment naming [souls]. */
    private fun pebble(
        id: String,
        happenedAt: String,
        souls: List<PathSoul>,
    ): PathMoment {
        val at = OffsetDateTime.parse(happenedAt)
        return PathMoment(
            id = id,
            name = id,
            happenedAt = at,
            createdAt = at,
            pebble = PathPebble(id = "pebble-$id", intensity = 2, positiveness = 0, souls = souls),
        )
    }

    private fun persons(
        moments: List<PathMoment>,
        weekStart: LocalDate = dstWeek,
        locale: Locale = en,
    ) = PersonsOfWeek.of(moments, weekStart, paris, locale)

    private fun days(vararg on: Boolean) = on.toList()

    @Test
    fun `marks the local day of each pebble, Monday first`() {
        val rows =
            persons(
                listOf(
                    // Monday 19 Oct 10:00 Paris (summer time, UTC+2).
                    pebble("a", "2026-10-19T08:00:00Z", listOf(soul("mara"))),
                    // Thursday 22 Oct 23:30 Paris is still Thursday, though it is 21:30Z.
                    pebble("b", "2026-10-22T21:30:00Z", listOf(soul("mara"))),
                ),
            )
        assertEquals(1, rows.size)
        assertEquals(days(true, false, false, true, false, false, false), rows[0].days.toList())
        assertEquals(2, rows[0].dayCount)
        assertEquals(2, rows[0].pebbleCount)
    }

    @Test
    fun `keeps a pebble at 23-59 on the Sunday DST ends on that Sunday`() {
        // Sunday 25 Oct 23:59 Paris is winter time (UTC+1): 22:59Z. Whole-second as
        // iOS and Android emit it, fractional as Postgres returns it.
        for (at in listOf("2026-10-25T22:59:00Z", "2026-10-25T22:59:59.123456+00:00")) {
            val row = persons(listOf(pebble("a", at, listOf(soul("mara"))))).single()
            assertEquals(days(false, false, false, false, false, false, true), row.days.toList())
        }
    }

    @Test
    fun `counts no one for a bare moment`() {
        val bare = pebble("b", "2026-10-20T08:00:00Z", emptyList()).copy(pebble = null)
        val rows = persons(listOf(bare, pebble("a", "2026-10-19T08:00:00Z", listOf(soul("mara")))))
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].dayCount)
        assertEquals(1, rows[0].pebbleCount)
        assertEquals(days(true, false, false, false, false, false, false), rows[0].days.toList())
    }

    @Test
    fun `leaves out pebbles of the week before or after`() {
        val rows =
            persons(
                listOf(
                    pebble("before", "2026-10-18T21:59:00Z", listOf(soul("mara"))), // Sun 18 Oct 23:59 Paris
                    pebble("after", "2026-10-25T23:00:00Z", listOf(soul("elio"))), // Mon 26 Oct 00:00 Paris
                ),
            )
        assertEquals(emptyList<PersonOfWeek>(), rows)
    }

    @Test
    fun `buckets correctly across the spring change too`() {
        // Summer time starts Sunday 29 Mar 2026 at 02:00: a 23-hour Sunday.
        val week = LocalDate.of(2026, 3, 23)
        val row =
            persons(
                listOf(pebble("a", "2026-03-29T21:59:00Z", listOf(soul("mara")))), // Sun 29 Mar 23:59 Paris
                week,
            ).single()
        assertTrue(row.days[6])
    }

    @Test
    fun `does not pull the next Monday into a week that lost an hour`() {
        // Monday 30 Mar 00:30 Paris is 6 days 23.5 hours after Monday 23 Mar.
        val week = LocalDate.of(2026, 3, 23)
        val rows = persons(listOf(pebble("a", "2026-03-29T22:30:00Z", listOf(soul("mara")))), week)
        assertEquals(emptyList<PersonOfWeek>(), rows)
    }

    @Test
    fun `orders by days present, then pebbles, then name`() {
        val zoe = soul("zoe", "Zoé")
        val ana = soul("ana", "Ana")
        val bea = soul("bea", "Béa")
        val cy = soul("cy", "Cy")
        val rows =
            persons(
                listOf(
                    pebble("1", "2026-10-19T08:00:00Z", listOf(zoe, ana, bea, cy)),
                    pebble("2", "2026-10-19T09:00:00Z", listOf(ana)),
                    pebble("3", "2026-10-20T08:00:00Z", listOf(zoe)),
                ),
                locale = fr,
            )
        // zoe: 2 days. ana: 1 day, 2 pebbles. bea and cy: 1 day, 1 pebble, by name.
        assertEquals(listOf("zoe", "ana", "bea", "cy"), rows.map { it.soul.id })
    }

    @Test
    fun `collates names by locale, accents included`() {
        val rows =
            persons(
                listOf(pebble("1", "2026-10-19T08:00:00Z", listOf(soul("e2", "Élio"), soul("e1", "Eva"), soul("z", "Zack")))),
                locale = fr,
            )
        assertEquals(listOf("Élio", "Eva", "Zack"), rows.map { it.soul.name })
    }

    @Test
    fun `breaks a full tie on id, so the order is deterministic`() {
        val rows = persons(listOf(pebble("1", "2026-10-19T08:00:00Z", listOf(soul("b", "Sam"), soul("a", "Sam")))))
        assertEquals(listOf("a", "b"), rows.map { it.soul.id })
    }

    @Test
    fun `counts a duplicated soul once`() {
        // The web also skips souls its map cannot name; here every soul carries
        // its name on the pebble, so only the duplicate rule remains.
        val rows = persons(listOf(pebble("1", "2026-10-19T08:00:00Z", listOf(soul("mara"), soul("mara")))))
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].pebbleCount)
    }

    @Test
    fun `compares rows by their days, not by array identity`() {
        val a = PersonOfWeek(soul("a"), booleanArrayOf(true, false, false, false, false, false, false), 1, 1)
        val b = PersonOfWeek(soul("a"), booleanArrayOf(true, false, false, false, false, false, false), 1, 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `shows eight rows and folds the rest`() {
        val ids = (0 until 14).map { "s" + it.toString().padStart(2, '0') }
        val rows = persons(listOf(pebble("1", "2026-10-19T08:00:00Z", ids.map { soul(it) })))
        val (shown, rest) = PersonsOfWeek.split(rows)
        assertEquals(8, PersonsOfWeek.CAP)
        assertEquals(8, shown.size)
        assertEquals(6, rest.size)
        assertEquals(rows, shown + rest)
    }

    @Test
    fun `folds nothing at or under the cap`() {
        assertEquals(emptyList<PersonOfWeek>(), PersonsOfWeek.split(emptyList()).second)
    }
}
