package app.pbbls.android.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [pebblesRowPosition] contract — mirrors iOS `pebblesRowPosition(index:count:)`
 * (`Theme/PebblesList.swift`): single-row sections are ONLY; multi-row sections
 * round only the edges each row owns.
 */
class PebblesListPositionTest {
    @Test
    fun `a single-row section is ONLY`() {
        assertEquals(PebblesListRowPosition.ONLY, pebblesRowPosition(index = 0, count = 1))
    }

    @Test
    fun `an empty or degenerate count is ONLY`() {
        assertEquals(PebblesListRowPosition.ONLY, pebblesRowPosition(index = 0, count = 0))
    }

    @Test
    fun `a two-row section is TOP then BOTTOM`() {
        assertEquals(PebblesListRowPosition.TOP, pebblesRowPosition(index = 0, count = 2))
        assertEquals(PebblesListRowPosition.BOTTOM, pebblesRowPosition(index = 1, count = 2))
    }

    @Test
    fun `interior rows of a long section are MIDDLE`() {
        assertEquals(PebblesListRowPosition.TOP, pebblesRowPosition(index = 0, count = 4))
        assertEquals(PebblesListRowPosition.MIDDLE, pebblesRowPosition(index = 1, count = 4))
        assertEquals(PebblesListRowPosition.MIDDLE, pebblesRowPosition(index = 2, count = 4))
        assertEquals(PebblesListRowPosition.BOTTOM, pebblesRowPosition(index = 3, count = 4))
    }
}
