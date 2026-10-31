package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsFamilyGroup
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerRing
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.MonthDone
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.StackLevel
import app.pbbls.android.core.model.achievements.TwentyFive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The margin ladders read the payload as bar lengths (#1307), and wear the done style only where
 * the server says so: a ring in `rings_done`, a listed level, a family with nothing not yet, a
 * coverage of `all`, a month on the rim. A fraction alone never does.
 */
class CardLaddersTest {
    private fun seat(
        k: Int,
        family: String,
        met: Boolean,
    ) = EmotionSeat(k, "e$k", family, met, on = null, pebble = null, isNew = false)

    private fun emotions(
        count: Int,
        of: Int,
        families: List<EmotionsFamilyGroup> = emptyList(),
    ) = EmotionsSection(count, of, seats = emptyList(), families = families, since = null, completion = null)

    @Test
    fun `a counting ladder is done at the listed levels, then the server's progress, then nothing`() {
        val pebbles =
            CountingSection(
                count = 137,
                levels = listOf(1, 10, 25, 50, 100).map { StackLevel(it, null, null, false) },
                next = NextLevel(250, 113, 0.25f),
                ladder = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                thenEvery = 500,
                since = null,
            )
        assertEquals(
            List(5) { LadderBar(1f, done = true) } + LadderBar(0.25f, done = false) + List(2) { LadderBar(0f, done = false) },
            CardLadders.counting(pebbles),
        )
    }

    @Test
    fun `a next step at full progress is still not done until the server lists it`() {
        val stack = CountingSection(10, emptyList(), NextLevel(10, 0, 1f), listOf(10, 25), thenEvery = null, since = null)
        assertEquals(LadderBar(1f, done = false), CardLadders.counting(stack).first())
    }

    @Test
    fun `the explorer ladder is one bar per ring, done when the server lists the ring`() {
        val explorer =
            ExplorerSection(
                firstPebble = null,
                firstFive = emptyList(),
                sixFirsts = emptyList(),
                twentyFive = TwentyFive(25, false, null, null, false),
                ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS),
                completion = null,
            )
        assertEquals(
            listOf(
                LadderBar(1f, done = true),
                LadderBar(1f, done = true),
                // 25 of 25 counted, but the ring is not listed done: full, not done.
                LadderBar(1f, done = false),
                LadderBar(0f, done = false),
                LadderBar(0.5f, done = false),
            ),
            CardLadders.explorer(explorer, emotions(19, 38), domains = null),
        )
    }

    @Test
    fun `the emotions ladder is one bar per served family, done when nothing in it is not yet`() {
        val joy = EmotionsFamilyGroup("joy", met = listOf(seat(0, "joy", true)), notYet = listOf(seat(1, "joy", false)))
        val pride = EmotionsFamilyGroup("pride", met = listOf(seat(2, "pride", true)), notYet = emptyList())
        assertEquals(
            listOf(LadderBar(0.5f, done = false), LadderBar(1f, done = true)),
            CardLadders.emotions(emotions(2, 3, listOf(joy, pride))),
        )
    }

    @Test
    fun `the domains bar is done at the all tier only`() {
        fun domains(
            count: Int,
            coverage: Coverage?,
        ) = DomainsSection(count, 18, coverage, emptyList(), emptyList(), null)
        assertEquals(LadderBar(1f, done = true), CardLadders.domains(domains(18, Coverage.ALL)))
        assertEquals(LadderBar(1f, done = false), CardLadders.domains(domains(18, Coverage.MOST)))
    }

    @Test
    fun `the year ladder places each month by its level and progress, done on the rim`() {
        fun m(
            month: Int,
            level: Int,
            progress: Float,
        ) = AchievementMonth(month, MonthState.KEPT, "joy", 0, 0, 0, 0, level, progress, MonthDone(false, false, false))
        val year =
            AchievementYear(
                2026,
                current = true,
                daysTarget = 14,
                months = listOf(m(1, 0, 0.5f), m(2, 1, 0.5f), m(3, 2, 0f)) + (4..12).map { m(it, 0, 0f) },
                listed = emptyList(),
                nextStep = null,
            )
        assertEquals(
            listOf(LadderBar(0.25f, done = false), LadderBar(0.75f, done = false), LadderBar(1f, done = true)),
            CardLadders.year(year).take(3),
        )
    }
}
