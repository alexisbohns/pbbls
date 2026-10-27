package app.pbbls.android.features.path

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.features.path.strip.PathPage
import app.pbbls.android.features.path.wrap.WrapState
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Path focuses a month page only once its pager settles on it (#1242 D20; `PathContent` passes
 * `focused = pagerState.settledPage == index`): a month dragged into view plays nothing and records
 * nothing, and its cover plays once the swipe settles there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-w412dp-h915dp-port-420dpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PathContentMonthFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val week = LocalDate.of(2026, 9, 28)
    private val ledger = IntroLedger()
    private var focus by mutableStateOf<PathPage>(PathPage.Week(week))

    @Test
    fun `a month dragged into view plays nothing, and its cover plays once the pager settles on it`() {
        // The month's clock reads System.nanoTime: the frame clock steps only when told, so its cover never lands.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    PathContent(
                        entries = emptyList(),
                        stripItems =
                            listOf(
                                StripItem.Week(
                                    weekStart = week,
                                    isoWeek = WeekRollBuilder.isoWeekNumber(week),
                                    isoYear = week.year,
                                    moments = 1,
                                    focusable = true,
                                    live = false,
                                    days = emptyList(),
                                ),
                                StripItem.Month(wrap.month, wrap.pebbles),
                            ),
                        focus = focus,
                        weekWraps = emptyMap(),
                        lookups = SpineFixtureWeek.lookups,
                        monthWraps = mapOf(wrap.month to WrapState.Ready(wrap)),
                        today = LocalDate.of(2026, 10, 26),
                        soulsById = emptyMap(),
                        onFocusChange = { focus = it },
                        paletteFor = { null },
                        modifier = Modifier.fillMaxSize(),
                        hapticsOn = { true },
                        introLedger = ledger,
                        tapHaptics = {},
                    )
                }
            }
        }
        settle()
        val pager = compose.onNodeWithTag(JourneyTags.PATH_WEEK_PAGER)

        // The month comes more than half into view under the finger: composed, not settled.
        pager.performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.3f, 0f))
            moveBy(Offset(-width * 0.3f, 0f))
        }
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue(compose.onAllNodesWithText(DECK_MARGIN, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // The Path's focus follows the current page, the month already; the page plays on the settled one.
        assertEquals(PathPage.Month(wrap.month), focus)
        assertFalse(ledger.contains(wrap.month, IntroSequence.Cover))

        pager.performTouchInput { up() }
        settle()
        assertEquals(PathPage.Month(wrap.month), focus)
        assertTrue(ledger.contains(wrap.month, IntroSequence.Cover))
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private companion object {
        const val FRAMES = 10

        /** The month's deck, by its margin: composed. */
        const val DECK_MARGIN = "October 2026"

        /** Long enough for a swipe to come to rest. */
        const val SETTLE_MS = 2_000L
    }
}
