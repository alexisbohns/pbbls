package app.pbbls.android.features.path.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompleteStepTest {
    @Test
    fun `three counted steps in the decided order, then success`() {
        assertEquals(listOf(CompleteStep.VALENCE, CompleteStep.EMOTION, CompleteStep.LIFE), CompleteStep.counted)
        assertNull(CompleteStep.SUCCESS.dotIndex)
        assertEquals(CompleteStep.SUCCESS, CompleteStep.LIFE.next)
    }

    @Test
    fun `emotion and life scroll themselves`() {
        assertEquals(setOf(CompleteStep.EMOTION, CompleteStep.LIFE), CompleteStep.entries.filter { it.bringsOwnScroll }.toSet())
    }
}
