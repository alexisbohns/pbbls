package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Process death (#857): the per-tab back stacks are saveable (#852), and a
 * screen holding what the server has not seen keeps it in a `SavedStateHandle`.
 * Neither is visible to a ViewModel test, and neither is exercised by a
 * configuration change — see [AppUiTest.restartAfterProcessDeath].
 */
@HiltAndroidTest
class RestorationTest : AppUiTest() {
    @Test
    fun `a cover survives process death with its parent under it`() {
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        onText(R.string.settings_title).assertIsDisplayed()

        restartAfterProcessDeath()
        signIn()

        onText(R.string.settings_title).assertIsDisplayed()
        pressBack()
        onText(R.string.profile_title).assertIsDisplayed()
    }

    /**
     * Capture keeps what the server has not seen in its `SavedStateHandle`
     * (A-5: no autosave): the typed name and the step come back on their own.
     */
    @Test
    fun `a capture step survives process death`() {
        skipOnboarding()
        launch()
        signIn()
        openPath()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_when_title).assertIsDisplayed()
        onText(R.string.record_action_continue).performClick()
        onText(R.string.record_name_title).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Coffee with Mo")

        restartAfterProcessDeath()
        signIn()

        onText(R.string.record_name_title).assertIsDisplayed()
        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
    }
}
