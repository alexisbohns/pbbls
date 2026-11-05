package app.pbbls.android.features.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** The step config (#1510 §3): count, ids, order and which steps can be passed. M69 inserts reminders after pace. */
class OnboardingStepsTest {
    @Test
    fun sevenStepsInSpecOrder() {
        assertEquals(
            listOf("promise", "consent", "reasons", "you", "people", "pace", "handoff"),
            OnboardingSteps.all.map { it.id },
        )
    }

    @Test
    fun idsAreUnique() {
        assertEquals(
            OnboardingSteps.all.size,
            OnboardingSteps.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun skipsMatchD1() {
        assertEquals(
            mapOf(
                "promise" to OnboardingSkip.NONE,
                "consent" to OnboardingSkip.NONE,
                "reasons" to OnboardingSkip.SKIP,
                "you" to OnboardingSkip.SKIP,
                "people" to OnboardingSkip.LATER,
                "pace" to OnboardingSkip.SKIP,
                "handoff" to OnboardingSkip.NONE,
            ),
            OnboardingSteps.all.associate { it.id to it.skip },
        )
    }

    @Test
    fun sixVisibleSteps() {
        assertEquals(6, OnboardingSteps.visible.size)
        assertEquals("pace", OnboardingSteps.visible.last().id)
    }
}
