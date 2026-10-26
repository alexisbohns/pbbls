package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.wrap.intro.HapticsSpy
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

/**
 * A month page plays its intro only while it is focused (#1242 D20): the Path's pager composes a neighbour
 * during a swipe, and that page must not play, or count its cover as played, until it settles on top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthPageIntroTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val ledger = IntroLedger()
    private val spy = HapticsSpy()
    private val intro = MonthIntro(ledger, spy)
    private var focused by mutableStateOf(false)

    @Test
    fun `an unfocused month plays nothing, and its cover plays on the frame it comes into focus`() {
        // The page's clock reads System.nanoTime: the virtual frame clock only steps when told, so nothing lands here.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    Box(Modifier.size(390.dp, 800.dp)) {
                        MonthPage(
                            wrap = wrap,
                            lookups = SpineFixtureWeek.lookups,
                            dayView = DayView.CIRCLE,
                            onDayView = {},
                            onWeek = {},
                            onOpen = {},
                            modifier = Modifier.fillMaxSize(),
                            intro = intro,
                            focused = focused,
                            zone = SpineFixtureWeek.zone,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue(spy.played.isEmpty())
        assertFalse(ledger.contains(wrap.month, IntroSequence.Cover))

        compose.runOnIdle { focused = true }
        // The write reaches the recomposer (no frame passes: the clock does not auto-advance), then one frame.
        compose.waitForIdle()
        assertTrue(spy.played.isEmpty())
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        assertTrue(ledger.contains(wrap.month, IntroSequence.Cover))
    }

    private companion object {
        const val FRAMES = 5
    }
}
