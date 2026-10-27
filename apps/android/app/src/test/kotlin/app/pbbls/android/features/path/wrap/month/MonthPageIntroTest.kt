package app.pbbls.android.features.path.wrap.month

import android.app.Application
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.wrap.intro.HapticsSpy
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroPolicy
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A month page plays its intro only while it is focused (#1242 D20): the Path's pager composes a neighbour
 * during a swipe, and that page must not play, or count its cover as played, until it settles on top. Nor
 * while its activity is not resumed; and under touch exploration or an accessibility font scale it is the
 * plain page, settled.
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

    /** The activity's lifecycle as the page reads it, moved by hand; resumed until told otherwise. */
    private val owner =
        object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }

    /**
     * The page, its clock reading System.nanoTime: the virtual frame clock only steps when told, so nothing lands
     * here by its time. [fontScale] null keeps the test's own.
     */
    private fun page(fontScale: Float? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides owner,
                        LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
                    ) {
                        Box(Modifier.size(390.dp, 800.dp)) {
                            MonthPage(
                                wrap = wrap,
                                lookups = SpineFixtureWeek.lookups,
                                dayView = DayView.CIRCLE,
                                onDayView = {},
                                onWeek = {},
                                onOpen = {},
                                focused = focused,
                                modifier = Modifier.fillMaxSize(),
                                intro = intro,
                                zone = SpineFixtureWeek.zone,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    /** The plain page, settled: no clock, nothing played, and no deck (its margin's month is the deck's alone). */
    private fun assertPlainPage() {
        assertTrue(spy.played.isEmpty())
        assertFalse(ledger.contains(wrap.month, IntroSequence.Cover))
        compose.onAllNodesWithText(DECK_MARGIN, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `an unfocused month plays nothing, and its cover plays on the frame it comes into focus`() {
        page()
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

    @Test
    fun `a paused activity lands the playing cover and stops its voice, and resumed it does not play again`() {
        focused = true
        page()
        // The deck: each card it has composed carries the margin.
        assertTrue(compose.onAllNodesWithText(DECK_MARGIN, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
        assertTrue(spy.stopped.isEmpty())

        // Another activity on top: started, no longer resumed.
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.stopped)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(listOf<IntroSequence>(IntroSequence.Cover), spy.played)
    }

    @Test
    fun `at an accessibility font scale the page is the plain one, settled`() {
        focused = true
        page(fontScale = IntroPolicy.LARGE_TEXT)
        assertPlainPage()
    }

    @Test
    fun `under touch exploration the page is the plain one, settled`() {
        val manager = ApplicationProvider.getApplicationContext<Application>().getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        focused = true
        page()
        assertPlainPage()
    }

    private companion object {
        const val FRAMES = 5

        /** The deck's margin: the month and its year, upright. */
        const val DECK_MARGIN = "October 2026"
    }
}
