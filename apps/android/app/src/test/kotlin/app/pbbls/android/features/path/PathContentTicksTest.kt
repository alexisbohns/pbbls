package app.pbbls.android.features.path

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.features.path.strip.PathPage
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Path's selection tick (#1242 D24; iOS `SpinePager` and `PathStrip`): a swipe that settles on another page
 * ticks once, a strip tap on another page ticks once at the tap and not again as the pager settles, and a page
 * the focus moves to from elsewhere, the focused strip item and a reload tick nothing. Motion is on, so the
 * pager animates to a steered page, as on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-w412dp-h915dp-port-420dpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PathContentTicksTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 26)
    private val ticks = mutableListOf<TapHaptic>()
    private var items by mutableStateOf((-3L..0L).map(::week))
    private var focus by mutableStateOf<PathPage>(page(0))

    /** The Monday [offset] weeks from this one; this week is live. */
    private fun monday(offset: Long): LocalDate = today.plusWeeks(offset)

    private fun week(offset: Long): StripItem.Week {
        val start = monday(offset)
        return StripItem.Week(
            weekStart = start,
            isoWeek = WeekRollBuilder.isoWeekNumber(start),
            isoYear = start.year,
            moments = 1,
            focusable = true,
            live = offset == 0L,
            days = emptyList(),
        )
    }

    private fun page(offset: Long): PathPage = PathPage.Week(monday(offset))

    private fun show() {
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    PathContent(
                        entries = emptyList(),
                        stripItems = items,
                        focus = focus,
                        weekWraps = emptyMap(),
                        lookups = SpineFixtureWeek.lookups,
                        today = today,
                        soulsById = emptyMap(),
                        onFocusChange = { focus = it },
                        paletteFor = { null },
                        modifier = Modifier.fillMaxSize(),
                        tapHaptics = { ticks += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** The strip cell of a past week, by what it says to a screen reader. */
    private fun stripCell(offset: Long) =
        compose.onNodeWithContentDescription(
            ApplicationProvider
                .getApplicationContext<Application>()
                .resources
                .getQuantityString(
                    R.plurals.path_strip_week_a11y,
                    1,
                    monday(offset).format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)),
                    1,
                ),
        )

    /** The page on screen, by its week line. */
    private fun assertOnPage(offset: Long) {
        val number = WeekRollBuilder.isoWeekNumber(monday(offset))
        compose
            .onNodeWithText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.path_wrap_week, number))
            .assertIsDisplayed()
    }

    @Test
    fun `a swipe that settles on another page ticks once`() {
        show()
        compose.onNodeWithTag(JourneyTags.PATH_WEEK_PAGER).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(page(-1), focus)
        assertOnPage(-1)
        assertEquals(listOf(TapHaptic.SELECTION), ticks)
    }

    @Test
    fun `a strip tap on another week ticks once at the tap, and not again as the pager settles there`() {
        show()
        compose.mainClock.autoAdvance = false
        stripCell(-2).performClick()
        assertEquals(listOf(TapHaptic.SELECTION), ticks)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(page(-2), focus)
        assertOnPage(-2)
        assertEquals(listOf(TapHaptic.SELECTION), ticks)
    }

    @Test
    fun `a tap on the focused strip item ticks nothing`() {
        focus = page(-1)
        show()
        stripCell(-1).performClick()
        compose.waitForIdle()
        assertOnPage(-1)
        assertEquals(emptyList<TapHaptic>(), ticks)
    }

    @Test
    fun `a page the focus moves to from elsewhere, a week link or a request, ticks nothing`() {
        show()
        compose.runOnIdle { focus = page(-3) }
        compose.waitForIdle()
        assertOnPage(-3)
        assertEquals(emptyList<TapHaptic>(), ticks)
    }

    @Test
    fun `a reload that adds an earlier week, moving the settled page's place, ticks nothing`() {
        show()
        compose.runOnIdle { items = listOf(week(-5)) + items }
        compose.waitForIdle()
        assertEquals(page(0), focus)
        assertOnPage(0)
        assertEquals(emptyList<TapHaptic>(), ticks)
    }
}
