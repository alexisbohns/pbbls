package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.features.consent.CONSENT_GATE_CONTINUE
import app.pbbls.android.features.consent.ConsentGateLogic
import app.pbbls.android.features.consent.consentRowTag
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeConsentService
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import javax.inject.Inject

/**
 * The auth gate end to end (#857): `RootViewModel`'s destination driving the one
 * `NavDisplay` in `RootScreen`, the onboarding push, and the parked invite —
 * none of which a ViewModel test can see, because the ordering lives in
 * `RootScreen`'s effects. Also the consent gate (#967) drawn above all of it:
 * [consents] is satisfied by default, so only the gate tests meet it.
 */
@HiltAndroidTest
class RootGateTest : AppUiTest() {
    @Inject lateinit var consents: FakeConsentService

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

    @Test
    fun `a consented account never sees the gate`() {
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `an account with no consent is held at the gate until it accepts`() {
        consents.rows.clear()
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        acceptEveryConsent()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
        assertEquals(4, consents.recordCalls.size)
    }

    @Test
    fun `the gate comes before onboarding`() {
        consents.rows.clear()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        acceptEveryConsent()

        onText(R.string.onboarding_skip).assertIsDisplayed()
    }

    @Test
    fun `back at the gate never reaches the stack beneath it`() {
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        onText(R.string.profile_title).assertIsDisplayed()

        // A different account, with nothing on record, lands on the same
        // stack; NavDisplay's Back would take it from You to Path.
        consents.rows.clear()
        signIn("user-2")
        pressBack()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        acceptEveryConsent()
        onText(R.string.profile_title).assertIsDisplayed()
    }

    @Test
    fun `an invite waits behind the gate`() {
        consents.rows.clear()
        skipOnboarding()
        launch(inviteIntent("tok-1"))
        signOut()
        signIn()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        onText(R.string.connections_accept_title).assertDoesNotExist()

        acceptEveryConsent()

        onText(R.string.connections_accept_title).assertIsDisplayed()
    }

    @Test
    fun `a failed check blocks, and retry lets a consented account through`() {
        consents.activeFailure = IOException("offline")
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_retry).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsNotDisplayed()

        consents.activeFailure = null
        onText(R.string.consent_gate_retry).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `logging out from the gate returns to Welcome`() {
        consents.rows.clear()
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_sign_out).performClick()

        assertEquals(1, supabase.signOutCount)
        onText(R.string.welcome_log_in).assertIsDisplayed()
    }

    private fun acceptEveryConsent() {
        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(consentRowTag(it)).performScrollTo().performClick() }
        compose.onNodeWithTag(CONSENT_GATE_CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()
    }
}
