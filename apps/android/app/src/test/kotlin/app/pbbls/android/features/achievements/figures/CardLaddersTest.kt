package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.EmotionSeat
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

/** The margin ladders read the payload as bar lengths (#1307): the server's levels, progress and rings. */
class CardLaddersTest {
    private fun emotions(
        count: Int,
        of: Int,
        seats: List<EmotionSeat> = emptyList(),
    ) = EmotionsSection(count, of, seats, families = emptyList(), since = null, completion = null)

    private fun seat(
        k: Int,
        family: String,
        met: Boolean,
    ) = EmotionSeat(k, "e$k", family, met, on = null, pebble = null, isNew = false)

    @Test
    fun `a counting ladder fills the listed levels, then the server's progress, then nothing`() {
        val pebbles =
            CountingSection(
                count = 137,
                levels = listOf(1, 10, 25, 50, 100).map { StackLevel(it, null, null, false) },
                next = NextLevel(250, 113, 0.25f),
                ladder = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
                thenEvery = 500,
                since = null,
            )
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f, 0.25f, 0f, 0f), CardLadders.counting(pebbles))
    }

    @Test
    fun `the explorer ladder is one bar per ring, full when the server says the ring is done`() {
        val explorer =
            ExplorerSection(
                firstPebble = null,
                firstFive = emptyList(),
                sixFirsts = emptyList(),
                twentyFive = TwentyFive(10, false, null, null, false),
                ringsDone = setOf(ExplorerRing.FIRST_FIVE, ExplorerRing.SIX_FIRSTS),
                completion = null,
            )
        assertEquals(listOf(1f, 1f, 0.4f, 0f, 0.5f), CardLadders.explorer(explorer, emotions(19, 38), domains = null))
    }

    @Test
    fun `the emotions ladder is one bar per family run`() {
        val seats = listOf(seat(0, "joy", true), seat(1, "joy", false), seat(2, "pride", true))
        assertEquals(listOf(0.5f, 1f), CardLadders.emotions(emotions(2, 3, seats)))
    }

    @Test
    fun `the year ladder places each month by its level and progress, the rim full`() {
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
        assertEquals(listOf(0.25f, 0.75f, 1f), CardLadders.year(year).take(3))
    }
}
