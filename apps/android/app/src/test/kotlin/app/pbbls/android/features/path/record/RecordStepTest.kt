package app.pbbls.android.features.path.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors iOS `RecordStepTests`. The step order is load-bearing (M58 D2) — three
 * sequencing dependencies pay for the flow's existence, and a reorder that keeps
 * the eleven screens has kept the cost and dropped the reason. This is the guard
 * against that reorder happening by accident.
 */
class RecordStepTest {
    @Test
    fun stepsAreInTheDesignedOrder() {
        assertEquals(
            listOf(
                RecordStep.PHOTO,
                RecordStep.WHEN,
                RecordStep.NAME,
                RecordStep.VALENCE,
                RecordStep.EMOTION,
                RecordStep.DOMAIN,
                RecordStep.SOULS,
                RecordStep.COLLECTION,
                RecordStep.GLYPH,
                RecordStep.PRIVACY,
                RecordStep.SUCCESS,
            ),
            RecordStep.entries.toList(),
        )
    }

    /** The photo's EXIF seeds the date, so it has to be picked first (D2). */
    @Test
    fun photoComesBeforeWhen() {
        assertTrue(RecordStep.PHOTO.ordinal < RecordStep.WHEN.ordinal)
    }

    /** Valence orders the emotion categories, so it has to be chosen first (D2). */
    @Test
    fun valenceComesBeforeEmotion() {
        assertTrue(RecordStep.VALENCE.ordinal < RecordStep.EMOTION.ordinal)
    }

    /** The grade belongs against the publish button, not eight fields away (D2). */
    @Test
    fun privacyIsTheLastCountedStep() {
        assertEquals(RecordStep.PRIVACY, RecordStep.counted.last())
    }

    @Test
    fun exactlyFourStepsAreOptional() {
        assertEquals(
            setOf(RecordStep.PHOTO, RecordStep.SOULS, RecordStep.COLLECTION, RecordStep.GLYPH),
            RecordStep.entries.filter { it.isOptional }.toSet(),
        )
    }

    @Test
    fun tenStepsAreCounted() {
        assertEquals(10, RecordStep.counted.size)
        assertTrue(RecordStep.SUCCESS !in RecordStep.counted)
    }

    @Test
    fun successIsUncountedAndTerminal() {
        assertNull(RecordStep.SUCCESS.dotIndex)
        assertNull(RecordStep.SUCCESS.next)
    }

    @Test
    fun dotIndexMatchesPositionForCountedSteps() {
        RecordStep.counted.forEachIndexed { index, step ->
            assertEquals("dot index for $step", index, step.dotIndex)
        }
    }

    @Test
    fun firstStepHasNoPrevious() {
        assertNull(RecordStep.PHOTO.previous)
        assertEquals(RecordStep.WHEN, RecordStep.PHOTO.next)
        assertEquals(RecordStep.PHOTO, RecordStep.WHEN.previous)
    }

    /** Every step has copy — the compiler cannot check a resource id is non-zero. */
    @Test
    fun everyStepCarriesATitle() {
        RecordStep.entries.forEach { step ->
            assertTrue("$step has no title resource", step.titleRes != 0)
        }
    }

    @Test
    fun onlyThreeStepsCarryASubtitle() {
        assertEquals(
            setOf(RecordStep.PHOTO, RecordStep.VALENCE, RecordStep.GLYPH),
            RecordStep.entries.filter { it.subtitleRes != null }.toSet(),
        )
    }
}
