package app.pbbls.android.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.ConsentGateLogic
import app.pbbls.android.core.model.OnboardingPace
import app.pbbls.android.core.model.OnboardingReason
import app.pbbls.android.features.consent.CONSENT_GATE_CONTINUE
import app.pbbls.android.features.consent.CONSENT_GATE_DELETE
import app.pbbls.android.features.consent.consentRowTag
import app.pbbls.android.features.onboarding.steps.onboardingConsentTag
import app.pbbls.android.features.onboarding.steps.onboardingPaceTag
import app.pbbls.android.features.onboarding.steps.onboardingReasonTag
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeConsentService
import app.pbbls.android.testing.FakeOnboardingService
import app.pbbls.android.testing.FakeProfileService
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

    @Inject lateinit var profile: FakeProfileService

    @Inject lateinit var onboardingService: FakeOnboardingService

    @Test
    fun `signed out lands on Welcome`() {
        launch()
        signOut()

        onText(R.string.welcome_log_in).assertIsDisplayed()
        // The suite keeps a hidden bar composed, slid off the bottom edge.
        onText(R.string.tab_home).assertIsNotDisplayed()
    }

    @Test
    fun `signed in lands on Home with the bar`() {
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.tab_home).assertIsSelected()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        onText(R.string.welcome_log_in).assertDoesNotExist()
    }

    @Test
    fun `a first sign-in walks the onboarding over Home and lands in Home`() {
        profile.profile = profile.profile.copy(onboardingCompleted = false)
        launch()
        signIn()

        // 1. promise
        onText(R.string.onboarding_promise_cta).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performClick()
        // 2. consent: the fake ledger is satisfied by default, so every switch is on and locked
        onText(R.string.onboarding_consent_title).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        // 3. reasons: one tile, then Continue
        compose.onNodeWithTag(onboardingReasonTag(OnboardingReason.KEEP_PEOPLE)).performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        // 4–5: skipped, Later
        compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick()
        // 6. pace: one pace, then Start completes
        compose.onNodeWithTag(onboardingPaceTag(OnboardingPace.WEEKLY_WRAP)).performScrollTo().performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        onText(R.string.onboarding_promise_cta).assertDoesNotExist()
        val answers = onboardingService.completeCalls.single()
        assertEquals(listOf(OnboardingReason.KEEP_PEOPLE), answers.reasons)
        assertEquals(OnboardingPace.WEEKLY_WRAP, answers.pace)
    }

    @Test
    fun `a completed profile never sees onboarding, even on a fresh device`() {
        profile.profile = profile.profile.copy(onboardingCompleted = true)
        launch()
        signIn()

        onText(R.string.onboarding_promise_cta).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
    }

    @Test
    fun `an invite parked before sign-in opens after Home`() {
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
        onText(R.string.onboarding_promise_cta).assertIsDisplayed()
        onText(R.string.connections_accept_title).assertDoesNotExist()

        walkOnboardingWithEverySkip()

        onText(R.string.connections_accept_title).assertIsDisplayed()
    }

    @Test
    fun `a consented account never sees the gate`() {
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
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
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        assertEquals(4, consents.recordCalls.size)
    }

    @Test
    fun `the gate stands down for onboarding and checks again when it ends`() {
        // The promise comes before the consent (#1510 D2): the flow's step 2
        // collects the acts, and the overlay only decides once the flow is done.
        consents.rows.clear()
        launch()
        signIn()

        onText(R.string.onboarding_promise_cta).assertIsDisplayed()
        onText(R.string.consent_gate_title).assertDoesNotExist()

        // Step 2 asks for every act and records them on Continue; the gate's re-check after the flow
        // finds the ledger satisfied and stands down without asking again.
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performClick()
        onText(R.string.onboarding_consent_title).assertIsDisplayed()
        onText(R.string.consent_gate_title).assertDoesNotExist()
        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(onboardingConsentTag(it)).performScrollTo().performClick() }
        compose.waitForIdle()
        assertEquals("a switch alone writes nothing", 0, consents.recordCalls.size)
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(4, consents.recordCalls.size)
        repeat(4) { compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick() }
        compose.waitForIdle()

        onText(R.string.consent_gate_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        assertEquals(4, consents.recordCalls.size)
    }

    @Test
    fun `a gate that failed at sign-in checks again once the flow ends`() {
        // Offline at sign-in: the gate's check fails behind the flow, whose own read a moment
        // later finds every act on record, so step 2 is locked on and records nothing. The
        // flow's end restarts the gate, and the ledger has lost an act by then.
        consents.activeFailure = IOException("offline")
        consents.activeFailsOnce = true
        launch()
        signIn()

        onText(R.string.onboarding_promise_cta).assertIsDisplayed()
        onText(R.string.consent_gate_retry).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performClick()
        onText(R.string.onboarding_consent_title).assertIsDisplayed()
        assertEquals(0, consents.recordCalls.size)
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        consents.rows.clear()
        repeat(4) { compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick() }
        compose.waitForIdle()

        onText(R.string.onboarding_promise_cta).assertDoesNotExist()
        onText(R.string.consent_gate_title).assertIsDisplayed()
        acceptEveryConsent()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
    }

    @Test
    fun `back at the gate never reaches the stack beneath it`() {
        skipOnboarding()
        launch()
        signIn()
        onText(R.string.tab_you).performClick()
        onYou().assertIsDisplayed()

        // A different account, with nothing on record, lands on the same
        // stack; NavDisplay's Back would take it from You to Home.
        consents.rows.clear()
        signIn("user-2")
        pressBack()

        onText(R.string.consent_gate_title).assertIsDisplayed()
        acceptEveryConsent()
        onYou().assertIsDisplayed()
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
        compose.onNodeWithTag(JourneyTags.HOME).assertIsNotDisplayed()

        consents.activeFailure = null
        onText(R.string.consent_gate_retry).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
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

    @Test
    fun `deleting from the gate signs out to Welcome without recording consent`() {
        consents.rows.clear()
        skipOnboarding()
        launch()
        signIn()

        compose.onNodeWithTag(CONSENT_GATE_DELETE).performScrollTo().performClick()
        onText(R.string.settings_delete_account_confirm).performClick()
        // The test session's token carries no sign-in stamp, so the recent
        // sign-in step comes first, as it would for a stale real session.
        onText(R.string.reauth_continue_google).performClick()
        compose.waitForIdle()

        assertEquals(1, supabase.googleReauthCount)
        assertEquals(1, profile.deleteAccountCount)
        assertEquals(1, supabase.signOutCount)
        assertEquals(0, consents.recordCalls.size)
        onText(R.string.welcome_log_in).assertIsDisplayed()
    }

    /** Through the flow with the fake's satisfied ledger: promise, consent (locked on), then four skips. */
    private fun walkOnboardingWithEverySkip() {
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performClick()
        compose.onNodeWithTag(JourneyTags.ONBOARDING_NEXT).performScrollTo().performClick()
        repeat(4) { compose.onNodeWithTag(JourneyTags.ONBOARDING_SKIP).performClick() }
        compose.waitForIdle()
    }

    private fun acceptEveryConsent() {
        // The row's toggle, not a tap at its centre: with real text metrics (NATIVE graphics)
        // the centre of a linked row lands on the link, which opens the document instead.
        ConsentGateLogic.REQUIRED.forEach { compose.onNodeWithTag(consentRowTag(it)).performSemanticsAction(SemanticsActions.OnClick) }
        compose.onNodeWithTag(CONSENT_GATE_CONTINUE).performScrollTo().performClick()
        compose.waitForIdle()
    }
}
