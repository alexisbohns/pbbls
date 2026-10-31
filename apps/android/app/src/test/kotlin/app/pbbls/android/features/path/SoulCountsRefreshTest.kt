package app.pbbls.android.features.path

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.create.pickers.SoulPickerSheet
import app.pbbls.android.features.path.flow.steps.RecordSoulsStep
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServices
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #1370: both soul pickers render each soul's count from the reference-data
 * cache, which loads once at sign-in and refreshes only on soul mutations. No
 * pebble write refreshes it, so each picker refreshes souls when it appears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class SoulCountsRefreshTest {
    @get:Rule
    val compose = createComposeRule()

    private val refs = FakeReferenceDataService(hasLoaded = true)

    @Test
    fun `the record flow's souls step refreshes souls when it appears`() {
        compose.setContent {
            PebblesTestHarness(graph = FakeServices(referenceData = refs)) {
                PebblesTheme { RecordSoulsStep(selectedIds = emptyList(), onToggle = {}) }
            }
        }
        compose.waitForIdle()
        assertEquals(1, refs.refreshSoulsCount)
    }

    @Test
    fun `the souls picker sheet refreshes souls when it appears`() {
        compose.setContent {
            PebblesTestHarness(graph = FakeServices(referenceData = refs)) {
                PebblesTheme { SoulPickerSheet(currentSelection = emptyList(), onDismiss = {}, onConfirm = {}) }
            }
        }
        compose.waitForIdle()
        assertEquals(1, refs.refreshSoulsCount)
    }
}
