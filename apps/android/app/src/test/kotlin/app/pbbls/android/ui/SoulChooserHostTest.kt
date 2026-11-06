package app.pbbls.android.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServices
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import app.pbbls.android.testing.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edit pebble's souls row opens the chooser sheet (#1519): Done writes the selection into the draft,
 * and anything else (back, the sheet's arrow) leaves the previous one standing (S2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class SoulChooserHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val refs =
        FakeReferenceDataService(
            souls = listOf(UiFixtures.soul(id = "s1", name = "Mo"), UiFixtures.soul(id = "s2", name = "Zed")),
            hasLoaded = true,
        )

    private var draft by mutableStateOf(PebbleDraft(soulIds = listOf("s2")))

    private fun str(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    private fun host(refs: FakeReferenceDataService = this.refs) {
        compose.setContent {
            PebblesTestHarness(graph = FakeServices(referenceData = refs)) {
                PebblesTheme {
                    var current by remember { mutableStateOf(draft) }
                    Column {
                        Text("ids:" + current.soulIds.joinToString(","))
                        PebbleForm(
                            draft = current,
                            onDraftChange = {
                                current = it
                                draft = it
                            },
                            domains = emptyList(),
                            souls = refs.souls,
                            collections = emptyList(),
                            selectedEmotion = null,
                            saveError = null,
                            hapticsOn = { false },
                        )
                    }
                }
            }
        }
    }

    private fun openSheet() {
        compose.onNodeWithText(str(R.string.create_soul_add)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `done writes the picked souls into the draft`() {
        host()
        openSheet()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        compose.onNodeWithText(str(R.string.souls_done_count, 2)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("s2", "s1"), draft.soulIds)
        compose.onAllNodesWithText(str(R.string.souls_done_count, 2)).assertCountEquals(0)
    }

    @Test
    fun `back and the sheet's arrow keep the previous selection`() {
        host()
        openSheet()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("s2"), draft.soulIds)
        compose.onAllNodesWithText(str(R.string.souls_done_count, 2)).assertCountEquals(0)

        openSheet()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription(str(R.string.onboarding_back)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("s2"), draft.soulIds)
    }

    @Test
    fun `a sheet opened before the souls load keeps its seed selection`() {
        val unloaded = FakeReferenceDataService(souls = emptyList(), hasLoaded = false)
        host(unloaded)
        openSheet()
        // One id chosen, though no soul is known yet: the unloaded empty list prunes nothing.
        compose.onNodeWithText(str(R.string.souls_done_count, 1)).assertExists()
        compose.onNodeWithText(str(R.string.souls_done_count, 1)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("s2"), draft.soulIds)
    }

    @Test
    fun `a failed load offers a retry rather than a spinner`() {
        val failed = FakeReferenceDataService(souls = emptyList(), hasLoaded = false, loadFailed = true)
        host(failed)
        openSheet()
        compose.onNodeWithText(str(R.string.souls_load_error)).assertExists()
        compose.onNodeWithText(str(R.string.profile_retry)).performClick()
        compose.waitForIdle()
        assertEquals(1, failed.loadCount)
    }

    @Test
    fun `back closes the search before the sheet`() {
        host()
        openSheet()
        compose.onNodeWithContentDescription(str(R.string.souls_search)).performClick()
        compose.onNodeWithText(str(R.string.souls_search)).performTextInput("Ze")
        Espresso.pressBack()
        compose.waitForIdle()
        // The field is gone and the sheet still stands.
        compose.onAllNodesWithText(str(R.string.souls_done_count, 1)).assertCountEquals(1)
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onAllNodesWithText(str(R.string.souls_done_count, 1)).assertCountEquals(0)
    }
}
