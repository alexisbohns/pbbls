package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.HapticsSpy
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A month's deck across a saved-state restore (#1242 D14, D21): within a launch (the Path's pager restoring a
 * page, a rotation) it reopens where it was; in a new launch (the process restored after it died, its ledger
 * new and empty) it is dealt again from the cover, which plays, as iOS always reopens the deck.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthPageRestoreTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val spy = HapticsSpy()

    /** The launch's intro: a new process makes a new ledger. Read as the page is composed again. */
    private var intro = MonthIntro(IntroLedger(), spy)
    private var focused by mutableStateOf(true)
    private var copy: WrapCopy? = null
    private val restore = StateRestorationTester(compose)

    private fun page() {
        compose.mainClock.autoAdvance = false
        restore.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    copy = rememberWrapCopy()
                    Box(Modifier.size(390.dp, 800.dp).testTag(PAGE)) {
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
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    /** The cover played, then a throw onto the weeks, which play: the deck rests on its second card. */
    private fun throwToTheWeeks(): String {
        page()
        compose.onNodeWithTag(PAGE).performTouchInput { swipeUp(startY = bottom - 1f, endY = top + 1f, durationMillis = 40) }
        settle()
        val weeks = MonthSections.head(MonthSection.WEEKS, wrap, SpineFixtureWeek.lookups, copy!!).label
        compose.onNodeWithText(weeks).assertIsDisplayed()
        assertEquals(listOf(IntroSequence.Cover, IntroSequence.Section(MonthSection.WEEKS)), spy.played)
        return weeks
    }

    /**
     * Saves the page's state, disposes it and composes it again from what was saved, as the activity does after
     * a rotation or a process death. The tester needs frames to pass, and a card that plays on the page's
     * System.nanoTime clock never lands on the test's frames: the page is unfocused around it, so nothing plays,
     * then focused again.
     */
    private fun restoreThePage() {
        compose.runOnIdle { focused = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        restore.emulateSavedInstanceStateRestore()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { focused = true }
        settle()
        // The restored deck's arrival comes a few frames after its focus, on this clock.
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `restored within a launch, the deck reopens on the card it rested on, and nothing plays again`() {
        val weeks = throwToTheWeeks()
        restoreThePage()
        compose.onNodeWithText(weeks).assertIsDisplayed()
        assertEquals(listOf(IntroSequence.Cover, IntroSequence.Section(MonthSection.WEEKS)), spy.played)
    }

    @Test
    fun `restored in a new launch, the deck is dealt again from the cover, which plays`() {
        val weeks = throwToTheWeeks()
        // The process died: its saved state comes back to a new ledger, empty.
        intro = MonthIntro(IntroLedger(), spy)
        restoreThePage()
        compose.onAllNodesWithText(weeks).assertCountEquals(0)
        compose.onNodeWithText(copy!!.monthName(wrap.month)).assertIsDisplayed()
        assertEquals(listOf(IntroSequence.Cover, IntroSequence.Section(MonthSection.WEEKS), IntroSequence.Cover), spy.played)
    }

    private companion object {
        const val PAGE = "page"

        /** Long enough for a fling to come to rest. */
        const val SETTLE_MS = 2_000L

        const val FRAMES = 5
    }
}
