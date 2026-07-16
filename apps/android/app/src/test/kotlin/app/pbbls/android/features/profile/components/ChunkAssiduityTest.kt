package app.pbbls.android.features.profile.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [chunkAssiduity] padding semantics — mirrors the iOS `AssiduityGrid` helper:
 * rows of exactly [columns], last row padded with `false`.
 */
class ChunkAssiduityTest {
    @Test
    fun `splits the 28-day window into four rows of seven`() {
        val rows = chunkAssiduity(List(28) { it % 2 == 0 }, columns = 7)
        assertEquals(4, rows.size)
        assertTrue(rows.all { it.size == 7 })
    }

    @Test
    fun `pads a short final row with false`() {
        val rows = chunkAssiduity(listOf(true, true, true, true, true), columns = 7)
        assertEquals(1, rows.size)
        assertEquals(listOf(true, true, true, true, true, false, false), rows.first())
    }

    @Test
    fun `empty data or degenerate columns produce no rows`() {
        assertTrue(chunkAssiduity(emptyList(), columns = 7).isEmpty())
        assertTrue(chunkAssiduity(listOf(true), columns = 0).isEmpty())
    }
}
