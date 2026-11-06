package app.pbbls.android.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.complete.CompleteFlowModel
import app.pbbls.android.features.path.complete.CompleteStep
import app.pbbls.android.features.path.flow.steps.RecordSoulsStep
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

/** The interim souls step (Part 3): the chooser inline, its tray the step's one button. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class RecordSoulsStepTest {
    @get:Rule
    val compose = createComposeRule()

    private val refs = FakeReferenceDataService(souls = listOf(UiFixtures.soul(id = "s1", name = "Mo")), hasLoaded = true)
    private val buzzes = mutableListOf<TapHaptic>()
    private val model = CompleteFlowModel(haptic = { buzzes += it }).also { it.goTo(CompleteStep.SOULS) }

    private fun str(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    private fun host() {
        compose.setContent {
            PebblesTestHarness(graph = FakeServices(referenceData = refs)) {
                PebblesTheme {
                    val flow by model.state.collectAsState()
                    RecordSoulsStep(
                        selectedIds = flow.answers.soulIds,
                        onToggle = model::toggleSoul,
                        onAdvance = model::advance,
                    )
                }
            }
        }
    }

    @Test
    fun `skip with nobody chosen advances`() {
        host()
        compose.onNodeWithText(str(R.string.record_action_skip)).performClick()
        compose.waitForIdle()
        assertEquals(CompleteStep.COLLECTION, model.step)
        assertEquals(emptyList<String>(), model.answers.soulIds)
    }

    @Test
    fun `a pick goes through the model and buzzes once, then done advances`() {
        host()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf("s1"), model.answers.soulIds)
        assertEquals(listOf(TapHaptic.SELECTION), buzzes)

        compose.onNodeWithText(str(R.string.souls_done_count, 1)).performClick()
        compose.waitForIdle()
        assertEquals(CompleteStep.COLLECTION, model.step)
        assertEquals(listOf(TapHaptic.SELECTION, TapHaptic.ADVANCE), buzzes)
    }
}
