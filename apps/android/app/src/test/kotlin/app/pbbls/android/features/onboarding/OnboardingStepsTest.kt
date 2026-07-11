package app.pbbls.android.features.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Ports iOS `OnboardingStepsTests`: count, unique ids, and spec id order. The
 * "non-empty copy" checks become non-zero string-resource ids.
 */
class OnboardingStepsTest {
    @Test
    fun containsExactlyFourSteps() {
        assertEquals(4, OnboardingSteps.all.size)
    }

    @Test
    fun stepIdsAreUnique() {
        val ids = OnboardingSteps.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun stepIdsMatchSpecOrder() {
        assertEquals(listOf("intro", "concept", "qualify", "carving"), OnboardingSteps.all.map { it.id })
    }

    @Test
    fun everyStepHasTitleAndDescriptionResources() {
        OnboardingSteps.all.forEach { step ->
            assertNotEquals("title unset for ${step.id}", 0, step.titleRes)
            assertNotEquals("description unset for ${step.id}", 0, step.descriptionRes)
        }
    }
}
