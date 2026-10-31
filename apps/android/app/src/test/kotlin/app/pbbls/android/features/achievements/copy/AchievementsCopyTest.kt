package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.MonthDone
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.StackLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/** The page's sentences over the real `values/strings.xml` (#1307, spec §4–§5): iOS's words, word for word. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementsCopyTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)

    private fun stack(
        count: Int,
        reached: List<Int>,
        next: NextLevel?,
        ladder: List<Int> = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
    ) = CountingSection(count, reached.map { StackLevel(it, null, null, false) }, next, ladder, thenEvery = 500, since = null)

    @Test
    fun `each coverage tier reads as its sentence, an unknown one as nothing`() {
        assertEquals("Your pebbles reach most parts of your life. Your insights have plenty to read.", copy.coverage(Coverage.MOST))
        assertEquals(
            "Link your pebbles to the parts of your life they touch, and your insights will start reading them.",
            copy.coverage(Coverage.NONE),
        )
        assertEquals("Every part of your life has a pebble in it. Your insights see the whole picture.", copy.coverage(Coverage.ALL))
        assertNull(copy.coverage(null))
    }

    @Test
    fun `the next step reads in days, in each of its forms, as iOS words it`() {
        assertEquals(
            "Twelve more days with a moment, one in each full week, bring November to the inner disc.",
            copy.nextStep(NextStep(11, Needs.MOMENTS, daysLeft = 12, reachable = true)),
        )
        assertEquals(
            "One more day with a pebble, and one in each full week, bring March to the rim.",
            copy.nextStep(NextStep(3, Needs.PEBBLES, daysLeft = 1, reachable = true)),
        )
        assertEquals(
            "Keep a moment in every full week and October reaches the inner disc.",
            copy.nextStep(NextStep(10, Needs.MOMENTS, daysLeft = 0, reachable = true)),
        )
        assertEquals("June can't reach the rim this month.", copy.nextStep(NextStep(6, Needs.PEBBLES, daysLeft = 4, reachable = false)))
        assertEquals(
            "June can't reach the inner disc this month.",
            copy.nextStep(NextStep(6, Needs.MOMENTS, daysLeft = 4, reachable = false)),
        )
        assertNull(copy.nextStep(NextStep(6, needs = null, daysLeft = 4, reachable = true)))
    }

    @Test
    fun `the skyline's line names the stones the server lists and the one rising`() {
        val pebbles = stack(137, listOf(1, 10, 25, 50, 100), NextLevel(250, 113, 0.25f))
        assertEquals("137 pebbles. Five stones stand; the sixth rises toward 250.", copy.pebblesLine(pebbles))
        assertEquals("1 pebble. One stone stands; the second rises toward 10.", copy.pebblesLine(stack(1, listOf(1), NextLevel(10, 9, 0f))))
        assertEquals(
            "0 pebbles. No stone stands yet; the first rises toward 1.",
            copy.pebblesLine(stack(0, emptyList(), NextLevel(1, 1, 0f))),
        )
        val past = stack(1200, listOf(1, 10, 25, 50, 100, 250, 500, 1000), NextLevel(1500, 300, 0.4f))
        assertEquals("1,200 pebbles. Every stone stands; the next mark is 1,500.", copy.pebblesLine(past))
        assertEquals("Every stone stands.", copy.stones(stack(60, listOf(1, 5, 10, 25, 50), null, listOf(1, 5, 10, 25, 50))))
        assertEquals("137 pebbles kept.", copy.pebblesKept(137))
    }

    @Test
    fun `counts, milestones, months and dates read in words and in the locale`() {
        assertEquals("five of eight", copy.ofWords(5, 8))
        assertEquals("23 of 38", copy.countOf(23, 38))
        assertEquals("of 38", copy.rosaceOf(38))
        assertEquals("113 to go", copy.toGo(113))
        assertEquals("then every 500", copy.thenEvery(500))
        assertEquals("November", copy.monthInSentence(11))
        assertEquals("November", copy.monthHeading(11))
        assertEquals("Apr 21", copy.shortDate(LocalDate.of(2026, 4, 21)))
        assertEquals("since Sunday, March 1, 2026", copy.since(LocalDate.of(2026, 3, 1)))
        assertEquals("since March 2", copy.sinceHint(LocalDate.of(2026, 3, 2)))
        assertEquals("day", copy.daysUnit(1))
        assertEquals("days", copy.daysUnit(249))
        assertEquals("No. 03", copy.registerNumber(3))
        assertEquals("Untitled", copy.momentName(null))
    }

    @Test
    fun `the ceremonies are titled as the catalog titles them`() {
        assertEquals("Every emotion", copy.ceremonyTitle(CompletionStack.EMOTIONS))
        assertEquals("Every domain", copy.ceremonyTitle(CompletionStack.DOMAINS))
        assertEquals("The six firsts", copy.ceremonyTitle(CompletionStack.SIX_FIRSTS))
    }

    @Test
    fun `the disc is read as the server's lists, a month row as its served counts`() {
        fun m(
            month: Int,
            level: Int,
            progress: Float,
        ) = AchievementMonth(month, MonthState.KEPT, "joy", 16, 14, 4, 4, level, progress, MonthDone(true, true, true))
        val months =
            (1..12).map {
                m(
                    it,
                    if (it == 8) {
                        2
                    } else if (it in 3..5) {
                        1
                    } else {
                        0
                    },
                    if (it == 11) 0.2f else 0f,
                )
            }
        assertEquals(
            "2026. On the rim: August. On the inner circle: March, April, May. On their way: November.",
            copy.discDescription(AchievementYear(2026, true, 14, months, emptyList(), null)),
        )
        assertEquals(
            "August. Days with a moment: 16, reached. With a pebble: 14, reached. Full weeks filled: 4 of 4, reached.",
            copy.monthDescription(months[7]),
        )
        assertEquals(
            "November. Days with a moment: 16, reached. With a pebble: 14. No full week yet.",
            copy.monthDescription(months[10].copy(fullWeeks = 0, weeksFilled = 0, done = MonthDone(true, false, false))),
        )
    }
}
