package app.pbbls.android.features.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Ports iOS `WelcomeStepsTests`: count, unique ids, and spec id order. The
 * "non-empty copy" checks become non-zero string-resource ids (JVM tests can't
 * resolve Android string values without Robolectric).
 */
class WelcomeStepsTest {
    @Test
    fun containsExactlyThreeSteps() {
        assertEquals(3, WelcomeSteps.all.size)
    }

    @Test
    fun stepIdsAreUnique() {
        val ids = WelcomeSteps.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun stepIdsMatchSpecOrder() {
        assertEquals(listOf("record", "enrich", "grow"), WelcomeSteps.all.map { it.id })
    }

    @Test
    fun everyStepHasTitleAndDescriptionResources() {
        WelcomeSteps.all.forEach { step ->
            assertNotEquals("title unset for ${step.id}", 0, step.titleRes)
            assertNotEquals("description unset for ${step.id}", 0, step.descriptionRes)
        }
    }
}
