package app.pbbls.android.features.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Onboarding-flag gating (the `RootView.onChange(of: session?.user.id)` rule):
 * present only when a user id appears while `hasSeenOnboarding` is still false.
 */
class OnboardingGateTest {
    @Test
    fun presentsWhenUserAppearsAndFlagUnset() {
        assertTrue(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false))
    }

    @Test
    fun doesNotPresentWhenAlreadySeen() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = true))
    }

    @Test
    fun doesNotPresentWithoutAUser() {
        assertFalse(OnboardingGate.shouldPresent(userId = null, hasSeenOnboarding = false))
    }

    @Test
    fun doesNotPresentWithoutAUserEvenIfUnseen() {
        assertFalse(OnboardingGate.shouldPresent(userId = null, hasSeenOnboarding = true))
    }
}
