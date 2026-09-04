package app.pbbls.android.features.path.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The valence roll's index arithmetic — ports iOS `ValenceRollTests`.
 *
 * What a test cannot cover, and what needs a device: the feel. Detent spacing,
 * the spring, and whether the vertical axis really wins against the step's
 * scroll container.
 */
class ValenceRollTest {
    @Test
    fun `the ladder runs large at the top to small at the bottom`() {
        assertEquals(
            listOf(ValenceSizeGroup.LARGE, ValenceSizeGroup.MEDIUM, ValenceSizeGroup.SMALL),
            ValenceSizeGroup.ladder,
        )
    }

    @Test
    fun `every cell of the grid resolves to exactly one valence`() {
        val cells =
            ValencePolarity.entries.flatMap { polarity ->
                ValenceSizeGroup.entries.map { size -> valenceAt(polarity, size) }
            }
        assertEquals(9, cells.size)
        assertEquals("every case is reachable exactly once", 9, cells.toSet().size)
    }

    @Test
    fun `indices round-trip through the grid`() {
        Valence.entries.forEach { valence ->
            assertEquals(valence, valenceAt(valence.polarityIndex, valence.sizeIndex))
        }
    }

    @Test
    fun `stepping moves one cell at a time`() {
        val start = Valence.NEUTRAL_MEDIUM
        assertEquals(Valence.LOWLIGHT_MEDIUM, valenceAt(start.polarityIndex - 1, start.sizeIndex))
        assertEquals(Valence.HIGHLIGHT_MEDIUM, valenceAt(start.polarityIndex + 1, start.sizeIndex))
        // The size axis runs large → small top to bottom, so a step *down* the
        // ladder is a smaller event.
        assertEquals(Valence.NEUTRAL_LARGE, valenceAt(start.polarityIndex, start.sizeIndex - 1))
        assertEquals(Valence.NEUTRAL_SMALL, valenceAt(start.polarityIndex, start.sizeIndex + 1))
    }

    @Test
    fun `the ends clamp instead of wrapping`() {
        assertEquals(Valence.LOWLIGHT_LARGE, valenceAt(-9, -9))
        assertEquals(Valence.HIGHLIGHT_SMALL, valenceAt(9, 9))
        // A hard swipe past the end must not loop back to where it started.
        assertEquals(Valence.HIGHLIGHT_MEDIUM, valenceAt(4, 1))
    }

    @Test
    fun `neighbour polarities stop at the ends`() {
        assertNull(Valence.LOWLIGHT_MEDIUM.polarityBefore)
        assertEquals(ValencePolarity.NEUTRAL, Valence.LOWLIGHT_MEDIUM.polarityAfter)
        assertEquals(ValencePolarity.LOWLIGHT, Valence.NEUTRAL_MEDIUM.polarityBefore)
        assertEquals(ValencePolarity.HIGHLIGHT, Valence.NEUTRAL_MEDIUM.polarityAfter)
        assertEquals(ValencePolarity.NEUTRAL, Valence.HIGHLIGHT_MEDIUM.polarityBefore)
        assertNull(Valence.HIGHLIGHT_MEDIUM.polarityAfter)
    }
}
