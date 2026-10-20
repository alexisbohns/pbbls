package app.pbbls.android.features.path

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The completion on the read page (pebble card spec §Completion), on the test clock: the
 * refresh that brings a bare moment's pebble keeps the bare page up under the arriving
 * card, the card's heading is silent until it lands, and once it has the page is the card
 * alone, verso up, and the ViewModel is told it was shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardCompletionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val versoHeading =
        hasContentDescription(context.getString(R.string.card_verso_heading), substring = true) and isHeading()

    @Before
    fun fullMotion() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    /**
     * Draws the last frame through to pixels before the test ends. A test that stopped
     * mid-print with frames still rendering under Robolectric's native graphics blanked a
     * later test's drawing in the same JVM (`DomainPostmarkPixelTest`).
     */
    @After
    fun flushFrame() {
        compose.onRoot().captureToImage()
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

    /**
     * Full motion, up to the press: the refresh that brings the pebble keeps the bare page up
     * under the printing card; nothing on the page can be pressed or read meanwhile (its call
     * to action is out of the semantics), and the card's heading is still silent. It stops
     * before the verso stone presses in: under Robolectric's native graphics a drawn glint
     * blanks the drawing of a later test in the same JVM (`DomainPostmarkPixelTest`), as
     * order-dependent as the `SpineGroundTest` failure the suite already shows locally. The
     * landing is the crossfade's and the recreation's.
     */
    @Test
    fun `under full motion the card prints over a silent bare page`() {
        val vm = completedOnScreen { PebbleDetailScreen(momentId = "m1", viewModel = it) }
        compose.mainClock.advanceTimeBy(PART_WAY_MS)

        assertTrue(vm.completing)
        assertPrinting()
    }

    /** Reduced motion: the page crossfades to the card in a quarter second, lands verso up, and says it was shown. */
    @Test
    fun `under reduced motion the page crossfades to the card and lands verso up`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val vm = completedOnScreen { PebbleDetailScreen(momentId = "m1", viewModel = it) }
        compose.mainClock.advanceTimeBy(FADE_PART_WAY_MS)

        assertTrue(vm.completing)
        assertPrinting()

        settle(PAST_THE_END_MS)
        assertLanded(vm)
    }

    /** A recreation mid-print lands at rest, verso up, without printing again, and tells the ViewModel. */
    @Test
    fun `a recreation mid-print lands at rest`() {
        val restoration = StateRestorationTester(compose)
        val vm =
            completedOnScreen(setContent = { content -> restoration.setContent(content) }) {
                PebbleDetailScreen(momentId = "m1", viewModel = it)
            }
        compose.mainClock.advanceTimeBy(PART_WAY_MS)
        assertPrinting()

        restoration.emulateSavedInstanceStateRestore()
        settle(FRAMES_MS)

        assertLanded(vm)
    }

    private val PebbleDetailViewModel.completing: Boolean
        get() = (uiState.value as PebbleDetailUiState.Content).completing

    private fun assertPrinting() {
        compose.onNodeWithTag(COMPLETION_PAGE_TAG).assertExists()
        compose.onNodeWithText(context.getString(R.string.complete_cta)).assertDoesNotExist()
        compose.onNode(versoHeading).assertDoesNotExist()
    }

    private fun assertLanded(vm: PebbleDetailViewModel) {
        assertFalse(vm.completing)
        compose.onNodeWithTag(COMPLETION_PAGE_TAG).assertDoesNotExist()
        compose.onNode(versoHeading).assertIsDisplayed()
    }

    private fun settle(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** A bare moment on [screen], then the resume after Complete finds its pebble; the clock is held. */
    private fun completedOnScreen(
        setContent: (@Composable () -> Unit) -> Unit = { content -> compose.setContent(content) },
        screen: @Composable (PebbleDetailViewModel) -> Unit,
    ): PebbleDetailViewModel {
        val service = FakeMomentReadService(moments = mapOf("m1" to UiFixtures.momentDetail(id = "m1", pebble = null)))
        val vm = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())
        setContent { PebblesTheme { PebblesTestHarness { screen(vm) } } }
        val cta = context.getString(R.string.complete_cta)
        compose.onNodeWithText(cta).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        service.moments = mapOf("m1" to UiFixtures.momentDetail(id = "m1"))
        // The resume after Complete: the composition's own was the first.
        compose.runOnIdle { vm.onResumed() }
        return vm
    }

    private companion object {
        const val PART_WAY_MS = 400L
        const val FADE_PART_WAY_MS = 100L
        const val PAST_THE_END_MS = 1_000L
        const val FRAMES_MS = 100L
    }
}
