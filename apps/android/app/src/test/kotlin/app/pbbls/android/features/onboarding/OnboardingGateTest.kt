package app.pbbls.android.features.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding gating (#1510 D10): a signed-in user whose profile says not completed, once per device. */
class OnboardingGateTest {
    @Test
    fun presentsWhenServerSaysNotCompleted() {
        assertTrue(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = false))
    }

    @Test
    fun doesNotPresentWhenServerSaysCompleted() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = true))
    }

    @Test
    fun doesNotPresentWhileTheProfileIsUnknown() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = false, onboardingCompleted = null))
    }

    @Test
    fun doesNotPresentTwiceOnOneDevice() {
        assertFalse(OnboardingGate.shouldPresent(userId = "user-1", hasSeenOnboarding = true, onboardingCompleted = false))
    }

    @Test
    fun doesNotPresentWithoutAUser() {
        assertFalse(OnboardingGate.shouldPresent(userId = null, hasSeenOnboarding = false, onboardingCompleted = false))
    }
}
