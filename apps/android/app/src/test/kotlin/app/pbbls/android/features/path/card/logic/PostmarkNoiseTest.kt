package app.pbbls.android.features.path.card.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostmarkNoiseTest {
    @Test
    fun `the same seed gives the same mask`() {
        assertArrayEquals(PostmarkNoise.alphas(64, seed = 7L), PostmarkNoise.alphas(64, seed = 7L))
    }

    @Test
    fun `different seeds give different masks`() {
        assertTrue(!PostmarkNoise.alphas(64, seed = 7L).contentEquals(PostmarkNoise.alphas(64, seed = 8L)))
    }

    @Test
    fun `the mask is restrained`() {
        val a = PostmarkNoise.alphas(128, seed = 3L)
        assertEquals(128 * 128, a.size)
        // Density never drops ink below 0.83 except at pits; pits are rare.
        val pits = a.count { it >= PostmarkNoise.PIT_ALPHA }
        val drift = a.filter { it < PostmarkNoise.PIT_ALPHA }
        assertTrue("pits ${pits.toFloat() / a.size}", pits.toFloat() / a.size in 0.01f..0.06f)
        assertTrue("drift max ${drift.max()}", drift.max() <= (255 * (1f - PostmarkNoise.MIN_DENSITY)).toInt() + 1)
    }
}
