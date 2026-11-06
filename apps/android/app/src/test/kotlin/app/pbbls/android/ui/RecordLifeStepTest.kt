package app.pbbls.android.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.complete.CompleteFlowModel
import app.pbbls.android.features.path.complete.CompleteStep
import app.pbbls.android.features.path.flow.steps.RecordLifeStep
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServices
import app.pbbls.android.testing.PIXEL_TEST_SDK
import app.pbbls.android.testing.PebblesTestHarness
import app.pbbls.android.testing.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Life page's own behaviour, on the flow's real state machine so every tap buzzes once. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class RecordLifeStepTest {
    @get:Rule
    val compose = createComposeRule()

    private val domains =
        listOf(
            Domain("d-1", "test-one", "Oneland", "Where one lives"),
            Domain("d-2", "test-two", "Twoland", "Where two lives"),
            Domain("d-3", "test-three", "Threeland", "Where three lives"),
            Domain("d-4", "test-four", "Fourland", "Where four lives"),
        )
    private val refs =
        FakeReferenceDataService(souls = listOf(UiFixtures.soul(id = "s1", name = "Mo").copy(pebblesCount = 2)), hasLoaded = true)
    private val buzzes = mutableListOf<TapHaptic>()
    private val model =
        CompleteFlowModel(haptic = { buzzes += it }).also {
            it.resume(PebbleAnswers(valence = Valence.HIGHLIGHT_MEDIUM, emotionId = "e-1"))
        }
    private var completes = 0

    private fun str(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    private fun host(
        recent: List<Domain> = emptyList(),
        souls: Boolean = true,
        loaded: Boolean = true,
        error: String? = null,
    ) {
        compose.setContent {
            PebblesTestHarness(graph = FakeServices(referenceData = refs)) {
                PebblesTheme {
                    val flow by model.state.collectAsState()
                    RecordLifeStep(
                        answers = flow.answers,
                        domains = domains,
                        recent = recent,
                        souls = if (souls) refs.souls else emptyList(),
                        isSoulsLoaded = loaded,
                        error = error,
                        isCompleting = flow.isCompleting,
                        hapticsOn = false,
                        onToggleDomain = model::toggleDomain,
                        onToggleSoul = model::toggleSoul,
                        onSetSouls = model::setSouls,
                        onComplete = { completes += 1 },
                    )
                }
            }
        }
    }

    @Test
    fun `lands on life and the button never carries a count`() {
        assertEquals(CompleteStep.LIFE, model.step)
        host()
        compose.onNodeWithText(str(R.string.complete_cta)).assertIsNotEnabled()
        model.toggleDomain("d-1")
        model.toggleDomain("d-2")
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.complete_cta)).performClick()
        assertEquals(1, completes)
    }

    @Test
    fun `the caption is absent until a tap, then names the last tap, also one that removed the domain`() {
        host()
        compose.onNodeWithText("Where one lives").assertDoesNotExist()
        compose.onNodeWithText("Oneland").performClick()
        compose.onNodeWithText("Where one lives").assertIsDisplayed()
        compose.onNodeWithText("Oneland").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), model.answers.domainIds)
        compose.onNodeWithText("Where one lives").assertIsDisplayed()
    }

    @Test
    fun `a fourth tap buzzes a warning and changes nothing`() {
        host()
        listOf("Oneland", "Twoland", "Threeland", "Fourland").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        assertEquals(listOf("d-1", "d-2", "d-3"), model.answers.domainIds)
        assertEquals(List(3) { TapHaptic.SELECTION } + TapHaptic.WARNING, buzzes)
    }

    @Test
    fun `recent shows only when there is any, and both copies of a domain reflect the selection`() {
        host(recent = listOf(domains[1]))
        compose.onNodeWithText(str(R.string.record_life_recent)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.record_life_all)).assertIsDisplayed()
        compose.onAllNodesWithText("Twoland").assertCountEquals(2)
        model.toggleDomain("d-2")
        compose.waitForIdle()
        compose.onAllNodes(hasText("Twoland") and isToggleable()).assertCountEquals(2)
    }

    @Test
    fun `no recent, no label`() {
        host()
        compose.onNodeWithText(str(R.string.record_life_recent)).assertDoesNotExist()
    }

    @Test
    fun `a strip tile toggles through the model and buzzes once`() {
        host()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        compose.waitForIdle()
        assertEquals(listOf("s1"), model.answers.soulIds)
        assertEquals(listOf(TapHaptic.SELECTION), buzzes)
    }

    @Test
    fun `nobody at all offers one add-people tile and not loaded offers none`() {
        host(souls = false)
        compose.onNodeWithText(str(R.string.souls_add_people)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.souls_more)).assertDoesNotExist()
    }

    @Test
    fun `before the souls load nobody is claimed to be missing`() {
        host(souls = false, loaded = false)
        compose.onNodeWithText(str(R.string.souls_add_people)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.souls_more)).assertDoesNotExist()
    }

    @Test
    fun `the failure line shows in the bar`() {
        host(error = str(R.string.complete_partial_failure))
        compose.onNodeWithText(str(R.string.complete_partial_failure)).assertIsDisplayed()
        assertTrue(buzzes.isEmpty())
    }
}
