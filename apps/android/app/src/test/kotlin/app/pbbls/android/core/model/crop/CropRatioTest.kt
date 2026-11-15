package app.pbbls.android.core.model.crop

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ported from iOS `CropRatioTests` (#1659). */
class CropRatioTest {
    @Test
    fun `the chips run 4-5, 3-4, 1-1, 4-3, 5-4`() {
        assertEquals(listOf("4:5", "3:4", "1:1", "4:3", "5:4"), CropRatio.entries.map { it.label })
    }

    @Test
    fun `value is width over height`() {
        assertEquals(0.8, CropRatio.FOUR_FIVE.value, 0.0)
        assertEquals(1.25, CropRatio.FIVE_FOUR.value, 0.0)
        assertEquals(1.0, CropRatio.SQUARE.value, 0.0)
    }

    @Test
    fun `a quarter turn swaps each format for its pair`() {
        val pairs =
            mapOf(
                CropRatio.FOUR_FIVE to CropRatio.FIVE_FOUR,
                CropRatio.THREE_FOUR to CropRatio.FOUR_THREE,
                CropRatio.SQUARE to CropRatio.SQUARE,
                CropRatio.FOUR_THREE to CropRatio.THREE_FOUR,
                CropRatio.FIVE_FOUR to CropRatio.FOUR_FIVE,
            )
        pairs.forEach { (ratio, expected) -> assertEquals(ratio.name, expected, ratio.paired) }
    }

    @Test
    fun `the editor opens on the format closest to the photo`() {
        val cases =
            listOf(
                3.0 / 4 to CropRatio.THREE_FOUR, // phone portrait
                4.0 / 3 to CropRatio.FOUR_THREE, // phone landscape
                9.0 / 16 to CropRatio.THREE_FOUR, // portrait screenshot
                16.0 / 9 to CropRatio.FOUR_THREE, // landscape video still
                2.0 / 3 to CropRatio.THREE_FOUR, // DSLR portrait
                1.0 to CropRatio.SQUARE,
                1.1 to CropRatio.SQUARE,
                0.8 to CropRatio.FOUR_FIVE,
            )
        cases.forEach { (ratio, expected) -> assertEquals("$ratio", expected, CropRatio.closest(ratio)) }
    }

    @Test
    fun `a ratio that is no ratio opens on square`() {
        assertEquals(CropRatio.SQUARE, CropRatio.closest(0.0))
        assertEquals(CropRatio.SQUARE, CropRatio.closest(-2.0))
        assertEquals(CropRatio.SQUARE, CropRatio.closest(Double.NaN))
        assertEquals(CropRatio.SQUARE, CropRatio.closest(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `the accessibility label reads the two numbers`() {
        assertEquals(4, CropRatio.FOUR_FIVE.width)
        assertEquals(5, CropRatio.FOUR_FIVE.height)
    }
}
