package app.pbbls.android.features.pebblemedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [encodeWithinBudget] — the quality ladder half of iOS `ImagePipeline`
 * (design D2): encode at the start quality, step down by 10 up to 3 times
 * while the budget busts, never below quality 10, then fail hard.
 */
class ImagePipelineCoreTest {
    @Test
    fun `first attempt within budget returns immediately`() {
        val qualities = mutableListOf<Int>()
        val out =
            encodeWithinBudget(startQuality = 85, byteCap = 100) { q ->
                qualities += q
                ByteArray(80)
            }
        assertEquals(80, out.size)
        assertEquals(listOf(85), qualities)
    }

    @Test
    fun `ladder steps down by 10 until the budget fits`() {
        val qualities = mutableListOf<Int>()
        encodeWithinBudget(startQuality = 85, byteCap = 100) { q ->
            qualities += q
            if (q <= 65) ByteArray(100) else ByteArray(500)
        }
        assertEquals(listOf(85, 75, 65), qualities)
    }

    @Test
    fun `exhausting all steps throws ImageTooLargeException`() {
        val qualities = mutableListOf<Int>()
        assertThrows(ImageTooLargeException::class.java) {
            encodeWithinBudget(startQuality = 85, byteCap = 100) { q ->
                qualities += q
                ByteArray(500)
            }
        }
        // iOS runs steps + 1 = 4 encode attempts before giving up.
        assertEquals(listOf(85, 75, 65, 55), qualities)
    }

    @Test
    fun `ladder stops early rather than encode at or below quality 10`() {
        val qualities = mutableListOf<Int>()
        assertThrows(ImageTooLargeException::class.java) {
            encodeWithinBudget(startQuality = 15, byteCap = 100) { q ->
                qualities += q
                ByteArray(500)
            }
        }
        // 15 → 5 would cross the floor, so only the first attempt runs.
        assertEquals(listOf(15), qualities)
    }

    @Test
    fun `budgets match the iOS constants`() {
        assertEquals(1024, SnapBudgets.ORIGINAL_MAX_EDGE_PX)
        assertEquals(420, SnapBudgets.THUMB_MAX_EDGE_PX)
        assertEquals(1_048_576, SnapBudgets.ORIGINAL_MAX_BYTES)
        assertEquals(307_200, SnapBudgets.THUMB_MAX_BYTES)
        assertEquals(85, SnapBudgets.ORIGINAL_START_QUALITY)
        assertEquals(75, SnapBudgets.THUMB_START_QUALITY)
    }
}
