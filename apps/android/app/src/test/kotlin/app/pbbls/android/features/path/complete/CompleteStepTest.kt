package app.pbbls.android.features.path.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompleteStepTest {
    @Test
    fun `six counted steps in the decided order, then success`() {
        assertEquals(
            listOf(
                CompleteStep.VALENCE,
                CompleteStep.EMOTION,
                CompleteStep.DOMAIN,
                CompleteStep.SOULS,
                CompleteStep.COLLECTION,
                CompleteStep.PRIVACY,
            ),
            CompleteStep.counted,
        )
        assertNull(CompleteStep.SUCCESS.dotIndex)
    }

    @Test
    fun `only souls and collection are optional`() {
        assertEquals(setOf(CompleteStep.SOULS, CompleteStep.COLLECTION), CompleteStep.entries.filter { it.isOptional }.toSet())
    }
}
