package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.intro.HapticsSpy
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroDrive
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroTick
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The deck's arrival (#1242 D21; iOS `MonthDeck`'s `onChange(of: shown)`): the card a throw settles on is
 * shown and plays, the one it covered lands, and a throw onto a card that plays nothing still ticks. The
 * clock reads the test's frame time, so a card plays and lands as the virtual clock runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthDeckPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups
    private val ledger = IntroLedger()
    private val spy = HapticsSpy()
    private val clock = IntroClock(wrap.month, ledger, spy, now = { compose.mainClock.currentTime * 1_000_000 })
    private val weeks = IntroSequence.Section(MonthSection.WEEKS)

    private fun deal(onWeek: (LocalDate) -> Unit = {}) {
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    Box(Modifier.size(390.dp, 800.dp).testTag(DECK)) {
                        IntroDrive(clock, active = true)
                        MonthDeckPager(
                            wrap,
                            lookups,
                            clock,
                            DayView.CIRCLE,
                            SpineFixtureWeek.zone,
                            aspect = null,
                            onWeek = onWeek,
                            onOpen = {},
                            onMore = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the cover plays as the deck is dealt, and a throw lands it and plays the next card`() {
        deal()
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertTrue(ledger.contains(wrap.month, IntroSequence.Cover))

        compose.onNodeWithTag(DECK).performTouchInput { swipeUp() }
        compose.waitForIdle()
        // The weeks' card ticks its own arrival, in its beats: the throw adds none.
        assertEquals(listOf(IntroSequence.Cover, weeks), spy.played)
        assertTrue(spy.ticks.isEmpty())
        assertTrue(ledger.contains(wrap.month, weeks))
    }

    @Test
    fun `a throw back onto a card that has played lands on the arrive tick`() {
        deal()
        compose.onNodeWithTag(DECK).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag(DECK).performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(listOf(IntroSequence.Cover, weeks), spy.played)
        assertEquals(listOf(IntroTick.arrive), spy.ticks)
    }

    @Test
    fun `a hard fling throws one card`() {
        deal()
        compose.onNodeWithTag(DECK).performTouchInput { swipeUp(startY = bottom - 1f, endY = top + 1f, durationMillis = 40) }
        compose.waitForIdle()
        assertEquals(listOf(IntroSequence.Cover, weeks), spy.played)
    }

    @Test
    fun `a tap lands the playing cover`() {
        compose.mainClock.autoAdvance = false
        deal()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertTrue(clock.isPlaying(IntroSequence.Cover))
        compose.onNodeWithTag(DECK).performTouchInput { click(center) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(IntroClock.Phase.Settled, clock.phase(IntroSequence.Cover))
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
    }

    @Test
    fun `a tap on the cover never reaches the weeks card held beneath it, whose taps focus their weeks once on top`() {
        val focused = mutableListOf<LocalDate>()
        deal(onWeek = { focused += it })
        // Down the cover's paper, over the weeks card held beneath it.
        tapDown()
        assertTrue("a tap on the cover reached the weeks: $focused", focused.isEmpty())
        // The weeks on top: the same taps focus their week.
        compose.onNodeWithTag(DECK).performTouchInput { swipeUp() }
        compose.waitForIdle()
        tapDown()
        assertTrue(focused.isNotEmpty())
    }

    /** Taps down the middle of the deck, from its head to below the cards' contents. */
    private fun tapDown() {
        DOWN.forEach { y ->
            compose.onNodeWithTag(DECK).performTouchInput { click(Offset(centerX, y.dp.toPx())) }
            compose.waitForIdle()
        }
    }

    private companion object {
        const val DECK = "deck"

        /** Where [tapDown] taps, in dp from the deck's top. */
        val DOWN = 100..700 step 25

        /** Past the clock's lead, well inside the cover's garland. */
        const val FRAMES = 10
    }
}
