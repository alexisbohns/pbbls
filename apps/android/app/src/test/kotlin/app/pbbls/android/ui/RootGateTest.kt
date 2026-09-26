package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The auth gate end to end (#857): `RootViewModel`'s destination driving the one
 * `NavDisplay` in `RootScreen`, the onboarding push, and the parked invite —
 * none of which a ViewModel test can see, because the ordering lives in
 * `RootScreen`'s effects.
 */
@HiltAndroidTest
class RootGateTest : AppUiTest() {
    @Test
    fun `signed out lands on Welcome`() {
        launch()
        signOut()

        onText(R.string.welcome_log_in).assertIsDisplayed()
        // The suite keeps a hidden bar composed, slid off the bottom edge.
        onText(R.string.tab_path).assertIsNotDisplayed()
    }

    @Test
    fun `signed in lands on Path with the bar`() {
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.tab_path).assertIsSelected()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        onText(R.string.welcome_log_in).assertDoesNotExist()
    }

    @Test
    fun `a first sign-in shows onboarding over Path`() {
        launch()
        signIn()

        onText(R.string.onboarding_skip).performClick()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `logging out from Profile replaces the stack with Welcome`() {
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        onText(R.string.profile_title).assertIsDisplayed()

        onText(R.string.profile_log_out).performScrollTo().performClick()

        assertEquals(1, supabase.signOutCount)
        onText(R.string.welcome_log_in).assertIsDisplayed()
        onText(R.string.tab_you).assertIsNotDisplayed()
        onText(R.string.profile_title).assertDoesNotExist()
    }

    @Test
    fun `an invite parked before sign-in opens after Path`() {
        skipOnboarding()
        launch(inviteIntent("tok-1"))
        signOut()
        onText(R.string.connections_accept_title).assertDoesNotExist()

        signIn()

        onText(R.string.connections_accept_title).assertIsDisplayed()
    }

    @Test
    fun `an invite parked before a first sign-in waits behind onboarding`() {
        launch(inviteIntent("tok-1"))
        signOut()
        signIn()
        onText(R.string.onboarding_skip).assertIsDisplayed()
        onText(R.string.connections_accept_title).assertDoesNotExist()

        onText(R.string.onboarding_skip).performClick()

        onText(R.string.connections_accept_title).assertIsDisplayed()
    }
}
