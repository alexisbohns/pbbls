package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentWriteService
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.inject.Inject

/**
 * Capture end to end (#1048): New pebble opens it, the three steps are walked
 * by tapping, `save_moment` gets the name, and the two saves land where A-14
 * says: Save moment back on Path, "Make it a pebble now" on Complete's first
 * step for the new moment.
 */
@HiltAndroidTest
class CaptureFlowTest : AppUiTest() {
    @Inject lateinit var writes: FakeMomentWriteService

    private fun onCaptureNameStep(name: String) {
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()
        onText(R.string.record_photo_title).assertIsDisplayed()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_when_title).assertIsDisplayed()
        onText(R.string.record_action_continue).performClick()
        onText(R.string.record_name_title).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput(name)
    }

    @Test
    fun `saving a capture returns to the path`() {
        onCaptureNameStep("Walk")

        onText(R.string.capture_save).performClick()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        onText(R.string.record_name_title).assertDoesNotExist()
        val saved = writes.saved.single()
        assertEquals("Walk", saved.name)
        assertNull(saved.snaps)
    }

    @Test
    fun `make it a pebble now saves and opens Complete for the new moment`() {
        writes.nextMomentId = "moment-9"
        onCaptureNameStep("Walk")

        onText(R.string.capture_make_pebble_now).performClick()

        onText(R.string.record_valence_title).assertIsDisplayed()
        assertEquals("Walk", writes.saved.single().name)

        // Complete replaced Capture, so leaving it lands on Path, not back on the name step.
        pressBack()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        onText(R.string.record_name_title).assertDoesNotExist()
    }
}
