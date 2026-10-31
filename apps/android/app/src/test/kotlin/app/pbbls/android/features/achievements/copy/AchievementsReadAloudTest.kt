package app.pbbls.android.features.achievements.copy

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.ExplorerRing
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

/**
 * The cards read aloud (#1307, spec §5) over the shared design fixture: iOS's labels
 * (`AchievementsA11yTests`), word for word. Each reads as the server's values, never the drawing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementsReadAloudTest {
    private val copy = AchievementsCopy(ApplicationProvider.getApplicationContext<Application>().resources, Locale.ENGLISH)
    private val page = AchievementsFixture.page

    @Test
    fun `the header reads the days kept, then since when`() {
        assertEquals("249 days, since Sunday, March 1, 2026", copy.headerDescription(249, LocalDate.of(2026, 3, 1)))
        assertEquals("1 day", copy.headerDescription(1, null))
    }

    @Test
    fun `Explorer reads each ring done or how far, then what is new on the wheel`() {
        assertEquals(
            "Explorer: the first five, done; the six firsts, done; 25 pebbles, done; domains, 14 of 18; emotions, 23 of 38. 3 new.",
            copy.explorerDescription(page.explorer!!, page.emotions, page.domains),
        )
    }

    @Test
    fun `an Explorer ring not done reads how far, and an absent section is left out`() {
        val explorer = page.explorer!!
        val partial =
            explorer.copy(
                ringsDone = setOf(ExplorerRing.SIX_FIRSTS),
                firstFive = explorer.firstFive.mapIndexed { i, item -> item.copy(done = i == 0, isNew = false) },
                sixFirsts = explorer.sixFirsts.map { it.copy(isNew = false) },
                twentyFive = null,
            )
        assertEquals(
            "Explorer: the first five, 1 of 5; the six firsts, done.",
            copy.explorerDescription(partial, emotions = null, domains = null),
        )
    }

    @Test
    fun `Emotions and Domains read the count, the coverage's meaning, what is new`() {
        assertEquals("Emotions, 23 of 38. 2 new.", copy.emotionsDescription(page.emotions!!))
        assertEquals(
            "Domains, 14 of 18. Your pebbles reach most parts of your life. Your insights have plenty to read. 1 new.",
            copy.domainsDescription(page.domains!!),
        )
    }

    @Test
    fun `Pebbles reads its line, then the glyph stacks the server sends, never an absent one as 0`() {
        assertEquals(
            "Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250. Glyphs carved: 12. Glyph sales: 3.",
            copy.pebblesDescription(page.pebbles!!, page.glyphsCarved, page.glyphSales),
        )
        assertEquals(
            "Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250.",
            copy.pebblesDescription(page.pebbles!!, null, null),
        )
    }

    @Test
    fun `the year reads as the server's lists, then the next step`() {
        assertEquals(
            "The year 2026. On the rim: August. On the inner disc: March, April, May, June, July, September, and October. " +
                "On their way: November. Twelve more days with a moment, one in each full week, bring November to the inner disc.",
            copy.yearDescription(page.years.first()),
        )
    }

    @Test
    fun `a ready mark says which ceremony it opens`() {
        assertEquals("The six firsts: the ceremony is ready.", copy.readyDescription(CompletionStack.SIX_FIRSTS))
    }
}
