package app.pbbls.android.features.path.wrap.month

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.SpineFixtureWeek
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroHapticPlaying
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroTick
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
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
import java.time.YearMonth

/**
 * The end card's Replay (#1242 D14; iOS `MonthPage.replay`): the month forgotten, a fresh clock, and the deck
 * dealt again from the cover, which plays at once, the page's focus carried over (D21's visibility trap).
 * The page's clock reads System.nanoTime and the test's frames are virtual, so a card never lands by its
 * time here: only a throw, a tap or the page's focus lands it, which is what these tests watch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthPageReplayTest {
    @get:Rule
    val compose = createComposeRule()

    private val wrap = SpineFixtureWeek.monthWrap
    private val lookups = SpineFixtureWeek.lookups
    private val cards = MonthDeck.cards(wrap, lookups)
    private val ledger = IntroLedger()
    private val voices = Voices()
    private val intro = MonthIntro(ledger, voices)
    private val replay = ApplicationProvider.getApplicationContext<Application>().getString(R.string.path_wrap_replay_month)
    private var focused by mutableStateOf(true)
    private var copy: WrapCopy? = null

    /** What the clocks asked of the haptics, in order, by card and month. */
    private class Voices : IntroHapticPlaying {
        val events = mutableListOf<String>()

        override fun play(
            sequence: IntroSequence,
            month: YearMonth,
            beats: List<IntroBeat>,
            startNanos: Long,
        ) {
            events += "play $sequence $month"
        }

        override fun stop(
            sequence: IntroSequence,
            month: YearMonth,
        ) {
            events += "stop $sequence $month"
        }

        override fun tick(tick: IntroTick) = Unit

        override fun frame(
            sequence: IntroSequence,
            month: YearMonth,
            nanos: Long,
        ) = Unit
    }

    private fun page(month: MonthWrap = wrap) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    copy = rememberWrapCopy()
                    Box(Modifier.size(390.dp, 800.dp).testTag(PAGE)) {
                        MonthPage(
                            wrap = month,
                            lookups = lookups,
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

    /** Lets a throw come to rest, a write recompose and an effect start: the clock never auto-advances here. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    /** A hard fling up, which throws one card. */
    private fun throwCard() {
        compose.onNodeWithTag(PAGE).performTouchInput { swipeUp(startY = bottom - 1f, endY = top + 1f, durationMillis = 40) }
        settle()
    }

    /**
     * Taps the end card's replay. Here the end card never lands by its time, so its replay is still hidden: the
     * first tap lands the card and leaves the replay be, and the second, on the shown replay, replays.
     */
    private fun tapReplay() {
        compose.onNodeWithText(replay).performClick()
        settle()
        compose.onNodeWithText(replay).performClick()
    }

    private fun played(month: YearMonth = wrap.month) = voices.events.filter { it.startsWith("play") && it.endsWith("$month") }

    @Test
    fun `the deck deals the end card last, and a hard fling throws one card all the way to it`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        assertEquals(IntroSequence.End, cards.last())
        assertEquals(cards.map { "play $it ${wrap.month}" }, played())
        // Only the top card speaks: the replay is there.
        compose.onNodeWithText(replay).assertIsDisplayed()
    }

    @Test
    fun `replay plays the cover again at once from a forgotten month, the end card landed on the old clock`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        assertEquals("play ${IntroSequence.End} ${wrap.month}", voices.events.last())

        tapReplay()
        settle()
        // The end card landed on the old clock, and the new deck's cover plays: nothing lands it.
        assertEquals(
            listOf("stop ${IntroSequence.End} ${wrap.month}", "play ${IntroSequence.Cover} ${wrap.month}"),
            voices.events.takeLast(2),
        )
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("play ${IntroSequence.Cover} ${wrap.month}", voices.events.last())
        // The month forgotten, then the cover recorded as it starts again.
        assertTrue(ledger.contains(wrap.month, IntroSequence.Cover))
        assertFalse(ledger.contains(wrap.month, IntroSequence.Section(MonthSection.WEEKS)))
        assertFalse(ledger.contains(wrap.month, IntroSequence.End))
        // Dealt from the cover: one throw reaches the weeks, which play again.
        throwCard()
        assertEquals("play ${IntroSequence.Section(MonthSection.WEEKS)} ${wrap.month}", voices.events.last())
    }

    @Test
    fun `a tap on the end card's hidden replay only lands the card, and replays nothing`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        compose.onNodeWithText(replay).performClick()
        settle()
        assertEquals("stop ${IntroSequence.End} ${wrap.month}", voices.events.last())
        assertEquals(1, played().count { it.startsWith("play ${IntroSequence.Cover}") })
        assertTrue(ledger.contains(wrap.month, IntroSequence.Section(MonthSection.WEEKS)))
        compose.onNodeWithText(replay).assertIsDisplayed()
    }

    @Test
    fun `the replayed deck plays only while the page is focused, as the first did`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        tapReplay()
        settle()
        assertEquals("play ${IntroSequence.Cover} ${wrap.month}", voices.events.last())

        compose.runOnIdle { focused = false }
        settle()
        assertEquals("stop ${IntroSequence.Cover} ${wrap.month}", voices.events.last())
        // Back in focus, the cover has played: it does not again.
        compose.runOnIdle { focused = true }
        settle()
        assertEquals(2, played().count { it.startsWith("play ${IntroSequence.Cover}") })
    }

    @Test
    fun `mid-fade the leaving deck says nothing, its replay gone, and the fade completes on the new cover`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        tapReplay()
        // The replay's frame, the new deal's, then about half the 300 ms fade: both decks are drawn, and only the new
        // one speaks (what TalkBack, Switch and Voice Access read: the merged tree).
        compose.mainClock.advanceTimeBy(HALF_FADE_MS)
        assertEquals("play ${IntroSequence.Cover} ${wrap.month}", voices.events.last())
        compose.onAllNodesWithText(replay).assertCountEquals(0)
        settle()
        compose.onAllNodesWithText(replay).assertCountEquals(0)
        // The new deck alone, on its cover: its month's name is the one heading on the page.
        compose.onAllNodesWithText(copy!!.monthName(wrap.month)).assertCountEquals(1)
    }

    @Test
    fun `a tap mid-fade only skips the new cover, and replays nothing more`() {
        page()
        repeat(cards.size - 1) { throwCard() }
        val button = compose.onNodeWithText(replay).getBoundsInRoot()
        tapReplay()
        compose.mainClock.advanceTimeBy(HALF_FADE_MS)
        assertEquals("play ${IntroSequence.Cover} ${wrap.month}", voices.events.last())
        val before = voices.events.size

        // Where the replay was: the new deck, on top, takes the tap.
        val at = with(compose.density) { Offset(((button.left + button.right) / 2).toPx(), ((button.top + button.bottom) / 2).toPx()) }
        compose.onNodeWithTag(PAGE).performTouchInput { click(at) }
        settle()
        assertEquals(listOf("stop ${IntroSequence.Cover} ${wrap.month}"), voices.events.drop(before))
        assertTrue(ledger.contains(wrap.month, IntroSequence.Cover))
        assertEquals(2, played().count { it.startsWith("play ${IntroSequence.Cover}") })
    }

    /** Two month pages on the Path's pager, this month and [next], each focused once the pager settles on it. */
    private fun path(next: MonthWrap): () -> PagerState {
        var pager: PagerState? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PebblesTheme {
                PebblesTestHarness {
                    copy = rememberWrapCopy()
                    val state = rememberPagerState { 2 }
                    pager = state
                    HorizontalPager(state, Modifier.size(390.dp, 800.dp).testTag(PATH)) { index ->
                        Box(Modifier.fillMaxSize().testTag(if (index == 0) PAGE else NEXT)) {
                            MonthPage(
                                wrap = if (index == 0) wrap else next,
                                lookups = lookups,
                                dayView = DayView.CIRCLE,
                                onDayView = {},
                                onWeek = {},
                                onOpen = {},
                                modifier = Modifier.fillMaxSize(),
                                intro = intro,
                                focused = state.settledPage == index,
                                zone = SpineFixtureWeek.zone,
                            )
                        }
                    }
                }
            }
        }
        settle()
        return { pager!! }
    }

    @Test
    fun `a swipe to the next month after a replay stays there`() {
        val august = wrap.copy(month = wrap.month.plusMonths(1))
        val pager = path(august)
        repeat(cards.size - 1) { throwCard() }
        tapReplay()
        settle()
        assertEquals("play ${IntroSequence.Cover} ${wrap.month}", voices.events.last())

        compose.onNodeWithTag(PATH).performTouchInput { swipeLeft() }
        settle()
        assertEquals(1, pager().settledPage)
        // Leaving lands the replayed cover; August's plays, and the Path stays on it.
        assertTrue("stop ${IntroSequence.Cover} ${wrap.month}" in voices.events)
        assertEquals("play ${IntroSequence.Cover} ${august.month}", voices.events.last())
        repeat(3) { settle() }
        assertEquals(1, pager().settledPage)
        assertEquals(1, pager().currentPage)
    }

    @Test
    fun `a replayed month keeps its deck where it was when the Path restores its page`() {
        val pager = path(wrap.copy(month = wrap.month.plusMonths(1)))
        repeat(cards.size - 1) { throwCard() }
        tapReplay()
        settle()
        throwCard()
        val weeks = MonthSections.head(MonthSection.WEEKS, wrap, lookups, copy!!).label
        compose.onNodeWithText(weeks).assertIsDisplayed()

        // The Path scrolls past the page, which leaves the composition, then restores it.
        compose.onNodeWithTag(PATH).performTouchInput { swipeLeft() }
        settle()
        assertEquals(1, pager().settledPage)
        compose.onNodeWithTag(PAGE).assertDoesNotExist()
        compose.onNodeWithTag(PATH).performTouchInput { swipeRight() }
        settle()
        assertEquals(0, pager().settledPage)
        // Still on the weeks, not back on the cover.
        compose.onNodeWithText(weeks).assertIsDisplayed()
    }

    private companion object {
        const val PAGE = "page"
        const val NEXT = "next"
        const val PATH = "path"

        /** Long enough for a fling to come to rest. */
        const val SETTLE_MS = 2_000L

        const val FRAMES = 10

        /** About half the replay's 300 ms cross-fade, past its first two frames. */
        const val HALF_FADE_MS = 150L
    }
}
