package app.pbbls.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.pbbls.android.R
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeProfileService
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * Settings' save sequence through the real screen (#857). The handle goes
 * first — the `public_profile` CHECK needs the handle stored before the flag
 * flips — then the rest, and nothing after a failed step is written.
 * `FakeProfileService.setHandleGate` holds the sequence open at its first
 * server call so a test can act while it is in flight.
 */
@HiltAndroidTest
class SettingsSaveTest : AppUiTest() {
    @Inject lateinit var profile: FakeProfileService

    private fun onHandleField(): SemanticsNodeInteraction =
        compose.onNode(hasSetTextAction() and hasText(string(R.string.settings_handle_label)))

    /** Settings over Profile, for an account that already has a handle and is private. */
    private fun openSettings() {
        profile.profile =
            ProfileRow(
                displayName = "Pebbler",
                createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                handle = "old_handle",
                publicProfile = false,
            )
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        onText(R.string.settings_title).assertIsDisplayed()
    }

    /** A new handle AND going public, in one save. */
    private fun editHandleAndGoPublic() {
        onHandleField().performScrollTo().performTextReplacement("new_handle")
        onText(R.string.settings_public_profile_toggle).performScrollTo().performClick()
    }

    @Test
    fun `the handle is written before anything else, and the rest follows it`() {
        val gate = CompletableDeferred<Unit>()
        profile.setHandleGate = gate
        openSettings()
        editHandleAndGoPublic()

        onText(R.string.action_save).performClick()

        assertEquals(listOf<String?>("new_handle"), profile.setHandleCalls)
        assertTrue("nothing after the handle while it is in flight", profile.saveSettingsCalls.isEmpty())
        assertTrue(profile.setPublicProfileCalls.isEmpty())

        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()

        assertEquals(1, profile.saveSettingsCalls.size)
        assertEquals(listOf(true), profile.setPublicProfileCalls)
        onText(R.string.profile_title).assertIsDisplayed()
    }

    @Test
    fun `cancel is ignored while a save is in flight`() {
        val gate = CompletableDeferred<Unit>()
        profile.setHandleGate = gate
        openSettings()
        editHandleAndGoPublic()
        onText(R.string.action_save).performClick()

        onText(R.string.action_cancel).performClick()

        onText(R.string.settings_title).assertIsDisplayed()
        compose.runOnIdle { gate.complete(Unit) }
    }

    @Test
    fun `leaving by system back mid-save does not cancel the rest of the sequence`() {
        val gate = CompletableDeferred<Unit>()
        profile.setHandleGate = gate
        openSettings()
        editHandleAndGoPublic()
        onText(R.string.action_save).performClick()

        pressBack()
        onText(R.string.profile_title).assertIsDisplayed()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()

        assertEquals(1, profile.saveSettingsCalls.size)
        assertEquals(listOf(true), profile.setPublicProfileCalls)
    }

    @Test
    fun `a failed handle claim stops the sequence and says so`() {
        openSettings()
        editHandleAndGoPublic()
        profile.failNext = IOException("offline")

        onText(R.string.action_save).performClick()

        onText(R.string.settings_save_error).performScrollTo().assertIsDisplayed()
        assertTrue(profile.saveSettingsCalls.isEmpty())
        assertTrue(profile.setPublicProfileCalls.isEmpty())
        onText(R.string.settings_title).assertIsDisplayed()
    }
}
