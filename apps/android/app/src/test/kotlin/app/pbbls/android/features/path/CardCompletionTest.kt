package app.pbbls.android.features.path

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.data.SignedUrlProviding
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.data.SnapBoundsDecoder
import app.pbbls.android.core.data.SnapURLCache
import app.pbbls.android.core.data.SnapUrls
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.card.COMPLETION_PAGE_TAG
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.PebblesTestHarness
import app.pbbls.android.testing.UiFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The completion on the read page (pebble card spec §Completion), on the test clock and on
 * semantics alone: the refresh that brings a bare moment's pebble keeps the bare page up,
 * silent and out of reach, under the printing card, whose heading is silent until it lands
 * verso up and the ViewModel is told it was shown; through the press under full motion, as
 * a crossfade under reduced motion, and after leaving mid-print and coming back.
 *
 * Robolectric's legacy graphics on purpose, with no `@GraphicsMode(NATIVE)`: drawn
 * natively, a print (the press's glint, or frames still rendering when a test ended)
 * blanked a later pixel test in the same JVM (`DomainPostmarkPixelTest`), even with the
 * last frame flushed. Nothing here reads a pixel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
class CardCompletionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun fullMotion() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun snapAspect(): SnapAspectCache {
        val scope = CoroutineScope(SupervisorJob())
        val provider =
            object : SignedUrlProviding {
                override suspend fun signedUrls(storagePrefix: String) = SnapUrls(original = "", thumb = "")
            }
        return SnapAspectCache(
            urls = SnapURLCache(provider, scope, nowMillis = { 0L }),
            scope = scope,
            decoder = SnapBoundsDecoder { null },
        )
    }

    private val cta get() = context.getString(R.string.complete_cta)
    private val versoHeading
        get() = hasContentDescription(context.getString(R.string.card_verso_heading), substring = true) and isHeading()

    /** Reduced motion: the page crossfades to the card in a quarter second and lands verso up. */
    @Test
    fun `under reduced motion the page crossfades to the card and lands verso up`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val vm = completedOnScreen { content -> compose.setContent(content) }
        compose.mainClock.advanceTimeBy(FADE_PART_WAY_MS)
        assertPrinting(vm)

        compose.mainClock.advanceTimeBy(PAST_THE_FADE_MS)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertLanded(vm)
    }

    /** Past the press (720 ms), short of the landing (1600 ms): a full print, not a 250 ms crossfade, is still running. */
    @Test
    fun `the card prints through the press and lands verso up`() {
        val vm = completedOnScreen { content -> compose.setContent(content) }
        compose.mainClock.advanceTimeBy(PAST_THE_PRESS_MS)
        assertPrinting(vm)

        compose.mainClock.advanceTimeBy(PAST_THE_RIPPLE_MS)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertLanded(vm)
    }

    /**
     * Leaving mid-print (Edit pebble, another tab) and coming back lands at rest. The screen
     * is disposed under a saveable holder, as Nav3 holds an entry, and its cancelled print
     * gets to run before the return: a write it made on its way out would reach the saved
     * state and replay the print. (`StateRestorationTester` cannot stand in: with the clock
     * held its save and restore land in one frame and nothing is disposed; with the clock
     * free the print finishes first.) A configuration change restores from a Bundle, a copy
     * no late write reaches, so this is the stricter case.
     */
    @Test
    fun `a print cut short by leaving lands at rest on return`() {
        var shown by mutableStateOf(true)
        val vm =
            completedOnScreen { content ->
                compose.setContent {
                    val holder = rememberSaveableStateHolder()
                    if (shown) holder.SaveableStateProvider("detail") { content() }
                }
            }
        compose.mainClock.advanceTimeBy(PART_WAY_MS)
        assertPrinting(vm)

        compose.runOnIdle { shown = false }
        compose.mainClock.advanceTimeBy(FRAMES_MS)
        compose.waitForIdle()
        assertTrue(vm.completing)

        compose.runOnIdle { shown = true }
        // Held clock: idle applies the return, one frame composes it, then a few more run it.
        // A print replayed from 0 would still show the page here; a free clock would let a
        // replay finish and pass.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAMES_MS)
        compose.onNodeWithTag(COMPLETION_PAGE_TAG).assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertLanded(vm)
    }

    /** A bare moment on screen through [setContent], then the resume after Complete finds its pebble; the clock is held. */
    private fun completedOnScreen(setContent: (@Composable () -> Unit) -> Unit): PebbleDetailViewModel {
        val service = FakeMomentReadService(moments = mapOf("m1" to UiFixtures.momentDetail(id = "m1", pebble = null)))
        val vm = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())
        setContent { PebblesTheme { PebblesTestHarness { PebbleDetailScreen(momentId = "m1", viewModel = vm) } } }
        compose.onNodeWithText(cta).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        service.moments = mapOf("m1" to UiFixtures.momentDetail(id = "m1"))
        // The resume after Complete: the composition's own was the first.
        compose.runOnIdle { vm.onResumed() }
        return vm
    }

    /** Mid-print: the page is up, silent, and its call to action out of reach; the heading is silent too. */
    private fun assertPrinting(vm: PebbleDetailViewModel) {
        assertTrue(vm.completing)
        compose.onNodeWithTag(COMPLETION_PAGE_TAG).assertExists()
        compose.onNodeWithText(cta).assertDoesNotExist()
        compose.onNode(versoHeading).assertDoesNotExist()
    }

    private fun assertLanded(vm: PebbleDetailViewModel) {
        assertFalse(vm.completing)
        compose.onNodeWithTag(COMPLETION_PAGE_TAG).assertDoesNotExist()
        compose.onNode(versoHeading).assertIsDisplayed()
    }

    private val PebbleDetailViewModel.completing: Boolean
        get() = (uiState.value as PebbleDetailUiState.Content).completing

    private companion object {
        const val PART_WAY_MS = 400L
        const val FADE_PART_WAY_MS = 100L
        const val PAST_THE_FADE_MS = 1_000L
        const val FRAMES_MS = 100L
        const val PAST_THE_PRESS_MS = 1_000L

        /** The print ends at 1600 ms; the press's last wave about 3.3 s in. */
        const val PAST_THE_RIPPLE_MS = 3_000L
    }
}
