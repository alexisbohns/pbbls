package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The decoder's tolerance (#1307, spec D17): precision variants, explicit nulls, omissions and
 * unknown values the dumps do not carry, which a client must survive without an error.
 */
class AchievementsToleranceTest {
    private fun snapshotAt(raw: String) =
        decodeAchievementsPage(
            """{"explorer": {"first_pebble": {"moment_id": "m1", "name": "x", "happened_at": "$raw",
               "emotion_id": "e1", "intensity": 2, "positiveness": 1}}}""",
        ).explorer!!.firstPebble

    @Test
    fun `a snapshot's timestamp decodes at every precision and offset spelling`() {
        val expected = Instant.parse("2026-03-02T09:00:00Z")
        for (raw in listOf(
            "2026-03-02T09:00:00Z",
            "2026-03-02T09:00:00.123Z",
            "2026-03-02T09:00:00.123456+00:00",
            "2026-03-02T10:00:00+01:00",
            "2026-03-02 09:00:00+00",
        )) {
            val at = snapshotAt(raw)!!.happenedAt
            assertEquals(raw, expected.epochSecond, at.epochSecond)
        }
    }

    @Test
    fun `a snapshot without a moment id or a readable time is dropped, the row stays`() {
        val page =
            decodeAchievementsPage(
                """{"pebbles": {"count": 1, "ladder": [1, 10], "levels": [
                    {"threshold": 1, "on": "2026-03-02", "new": false, "pebble": {"name": "gone"}},
                    {"threshold": 10, "on": "2026-03-09", "new": false, "pebble": {"moment_id": "m", "happened_at": "soon"}}]}}""",
            )
        val levels = page.pebbles!!.levels
        assertEquals(listOf(1, 10), levels.map { it.threshold })
        assertTrue(levels.all { it.pebble == null })
        assertEquals(LocalDate.of(2026, 3, 2), levels[0].on)
    }

    @Test
    fun `explicit nulls read as absent`() {
        val page =
            decodeAchievementsPage(
                """{"since": null, "days_kept": null, "years": null, "seen": null, "shelf": null, "shelf_count": null,
                    "explorer": {"first_pebble": null, "first_five": null, "six_firsts": null,
                    "twenty_five": null, "rings_done": null, "completion": null},
                    "emotions": {"count": null, "of": 38, "seats": null, "families": null, "since": null},
                    "pebbles": {"count": 0, "next": null, "then_every": null, "levels": null, "ladder": null}}""",
            )
        val explorer = page.explorer!!
        assertNull(explorer.firstPebble)
        assertTrue(explorer.firstFive.isEmpty())
        assertTrue(explorer.sixFirsts.isEmpty())
        assertNull(explorer.twentyFive)
        assertTrue(explorer.ringsDone.isEmpty())
        assertNull(explorer.completion)
        assertTrue(page.years.isEmpty())
        assertTrue(page.seen.isEmpty())
        assertTrue(page.shelf.isEmpty())
        assertEquals(0, page.shelfCount)
        assertEquals(0, page.emotions!!.count)
        assertTrue(page.emotions!!.families.isEmpty())
        assertNull(page.pebbles!!.next)
        assertNull(page.pebbles!!.thenEvery)
    }

    @Test
    fun `an omitted section hides its card, an empty body is an empty page`() {
        val page = decodeAchievementsPage("{}")
        assertNull(page.explorer)
        assertNull(page.emotions)
        assertNull(page.domains)
        assertNull(page.pebbles)
        assertNull(page.glyphsCarved)
        assertNull(page.glyphSales)
        assertNull(page.since)
        assertNull(page.daysKept)
        assertTrue(page.years.isEmpty())
        assertTrue(page.seen.isEmpty())
        assertTrue(page.shelf.isEmpty())
    }

    @Test
    fun `unknown semantic values fall back, never throw`() {
        val page =
            decodeAchievementsPage(
                """{"domains": {"count": 2, "of": 18, "coverage": "plenty", "entered": [], "not_yet": []},
                    "explorer": {"rings_done": ["first_five", "moons"], "six_firsts": [{"kind": "tiny", "done": true}],
                                 "first_five": [{"slug": "first-moon", "done": true},
                                                {"slug": "first-soul", "done": true, "subject": {"kind": "ghost", "id": "s"}}]},
                    "seen": {"explorer": "2026-10-02T08:14:05Z", "attic": "2026-10-02T08:14:05Z", "year": "whenever"},
                    "years": [{"year": 2026, "current": true, "listed": [1, 13],
                       "next_step": {"month": 1, "needs": "stars", "days_left": 3, "reachable": true},
                       "months": [${(1..12).joinToString {
                    """{"month": $it, "state": "eclipsed", "family": "awe", "level": 7, "progress": 3.5}"""
                }}]}]}""",
            )
        assertNull(page.domains!!.coverage)
        assertEquals(setOf(ExplorerRing.FIRST_FIVE), page.explorer!!.ringsDone)
        assertTrue(page.explorer!!.sixFirsts.isEmpty())
        val soul = page.explorer!!.firstFive.single()
        assertEquals(FirstStep.SOUL, soul.step)
        assertNull(soul.subject)
        assertEquals(setOf(AchievementCard.EXPLORER), page.seen.keys)
        val year = page.years.single()
        assertNull(year.nextStep)
        assertNull(year.daysTarget)
        assertEquals(listOf(1), year.listed)
        val january = year.months.first()
        assertEquals(MonthState.QUIET, january.state)
        assertEquals("awe", january.family)
        assertEquals(2, january.level)
        assertEquals(1f, january.progress)
        assertEquals(MonthDone(moments = false, pebbles = false, weeks = false), january.done)
    }

    @Test
    fun `a year without twelve distinct months is dropped, a full one is kept in month order`() {
        val months = (12 downTo 1).joinToString { """{"month": $it, "state": "quiet"}""" }
        val page =
            decodeAchievementsPage(
                """{"years": [{"year": 2026, "months": [{"month": 1}]}, {"year": 2025, "months": [$months]}]}""",
            )
        assertEquals(listOf(2025), page.years.map { it.year })
        assertEquals(
            (1..12).toList(),
            page.years
                .single()
                .months
                .map { it.month },
        )
    }

    @Test
    fun `a seat without an emotion id is dropped, the rest keep their seat, and a family skips seats it cannot find`() {
        val page =
            decodeAchievementsPage(
                """{"emotions": {"count": 1, "of": 3, "seats": [
                    {"seat": 2, "emotion_id": "c", "family": "peace"}, {"seat": 1},
                    {"seat": 0, "emotion_id": "a", "family": "joy", "met": true, "on": "2026-03-02"}],
                    "families": [{"family": "joy", "met": [0], "not_yet": [1]}, {"met": [], "not_yet": [2]},
                                 {"family": "peace", "met": [], "not_yet": [2, 9]}]}}""",
            )
        val emotions = page.emotions!!
        assertEquals(listOf(0, 2), emotions.seats.map { it.seat })
        assertEquals(listOf("joy", "peace"), emotions.families.map { it.family })
        assertEquals(listOf("a"), emotions.families[0].met.map { it.emotionId })
        assertTrue(emotions.families[0].notYet.isEmpty())
        assertEquals(listOf("c"), emotions.families[1].notYet.map { it.emotionId })
        assertEquals(LocalDate.of(2026, 3, 2), emotions.seats[0].on)
    }

    @Test
    fun `a ready completion and a pulled one decode, a broken one is dropped`() {
        val page =
            decodeAchievementsPage(
                """{"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-01T08:00:00Z", "offered_from": "2026-10-02T04:00:00Z", "ready": true,
                                     "pebble": {"moment_id": "m", "happened_at": "2026-09-30T18:00:00Z", "intensity": 3, "positiveness": 1}}},
                    "domains": {"count": 18, "of": 18, "coverage": "all",
                      "completion": {"completed_at": "2026-10-01T08:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-03T07:30:00.5+00:00"}},
                    "explorer": {"completion": {"ready": true}}}""",
            )
        val emotions = page.emotions!!.completion!!
        assertTrue(emotions.ready)
        assertEquals(Valence.HIGHLIGHT_LARGE, emotions.pebble!!.valence)
        assertNotNull(page.domains!!.completion!!.pulledAt)
        assertNull(page.domains!!.completion!!.pebble)
        assertEquals(Coverage.ALL, page.domains!!.coverage)
        assertNull(page.explorer!!.completion)
    }

    @Test
    fun `a top-of-ladder stack has no next, out-of-range values clamp, the days decode`() {
        val page =
            decodeAchievementsPage(
                """{"since": "2026-03-01", "days_kept": 249,
                    "glyph_sales": {"count": 60, "ladder": [1, 5, 10, 25, 50], "levels": [], "since": "2026-04-01"},
                    "pebbles": {"count": 3, "ladder": [1, 10], "next": {"threshold": 10, "left": 7, "progress": 1.7}}}""",
            )
        assertEquals(LocalDate.of(2026, 3, 1), page.since)
        assertEquals(249, page.daysKept)
        assertNull(page.glyphSales!!.next)
        assertEquals(LocalDate.of(2026, 4, 1), page.glyphSales!!.since)
        assertEquals(1f, page.pebbles!!.next!!.progress)
        assertNull(page.pebbles!!.since)
    }

    @Test
    fun `an out-of-range valence falls back to medium even, as the Path's does`() {
        val first =
            decodeAchievementsPage(
                """{"explorer": {"first_pebble": {"moment_id": "m", "happened_at": "2026-03-02T09:00:00Z", "intensity": 9}}}""",
            ).explorer!!.firstPebble!!
        assertEquals(Valence.NEUTRAL_MEDIUM, first.valence)
        assertNull(first.emotionId)
        assertNull(first.name)
    }

    @Test
    fun `a shelf row without a slug or a family is dropped, an unreadable day kept as null`() {
        val page =
            decodeAchievementsPage(
                """{"shelf": [{"slug": "pebble-count-10", "family": "pebble_count", "threshold": 10, "unlocked_on": "2026-10-30"},
                              {"family": "first_soul", "threshold": null}, {"slug": "first-soul", "family": "first_soul",
                               "threshold": null, "unlocked_on": "yesterday"}], "shelf_count": 9}""",
            )
        assertEquals(
            listOf(
                ShelfItem("pebble-count-10", "pebble_count", 10, LocalDate.of(2026, 10, 30)),
                ShelfItem("first-soul", "first_soul", null, null),
            ),
            page.shelf,
        )
        assertEquals(9, page.shelfCount)
    }

    @Test
    fun `a null inside a list or a map is dropped, its neighbours kept`() {
        val page =
            decodeAchievementsPage(
                """{"seen": {"explorer": null, "emotions": "2026-10-02T08:14:05Z"},
                    "explorer": {"rings_done": [null, "six_firsts"]},
                    "emotions": {"count": 1, "of": 2, "seats": [
                        {"seat": 0, "emotion_id": "a", "family": "joy", "met": true},
                        {"seat": 1, "emotion_id": "b", "family": "joy"}],
                      "families": [{"family": "joy", "met": [null, 0], "not_yet": [1, null]}]},
                    "domains": {"count": 0, "of": 2, "coverage": "none", "entered": [], "not_yet": ["d1", null, "d2"]},
                    "pebbles": {"count": 0, "ladder": [1, null, 10], "levels": []},
                    "years": [{"year": 2026, "current": true, "listed": [10, null, 9],
                       "months": [${(1..12).joinToString { """{"month": $it, "state": "quiet"}""" }}]}]}""",
            )
        assertEquals(setOf(AchievementCard.EMOTIONS), page.seen.keys)
        assertEquals(setOf(ExplorerRing.SIX_FIRSTS), page.explorer!!.ringsDone)
        val joy = page.emotions!!.families.single()
        assertEquals(listOf("a"), joy.met.map { it.emotionId })
        assertEquals(listOf("b"), joy.notYet.map { it.emotionId })
        assertEquals(listOf("d1", "d2"), page.domains!!.notYet)
        assertEquals(listOf(1, 10), page.pebbles!!.ladder)
        assertEquals(listOf(10, 9), page.years.single().listed)
    }
}
