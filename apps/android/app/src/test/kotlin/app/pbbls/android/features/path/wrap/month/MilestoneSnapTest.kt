package app.pbbls.android.features.path.wrap.month

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `MonthCardsTests.polaroidShapes` and `.polaroid` (#1241 D16). */
class MilestoneSnapTest {
    private val wide = 4f / 3
    private val tall = 3f / 4

    @Test
    fun `a polaroid is square, 3 by 4 or 4 by 3, the nearest to its photo`() {
        assertEquals(wide, MilestoneSnapShape.shape(16f / 9), 0f)
        assertEquals(wide, MilestoneSnapShape.shape(wide), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(1.1f), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(1f), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(0.9f), 0f)
        assertEquals(tall, MilestoneSnapShape.shape(tall), 0f)
        assertEquals(tall, MilestoneSnapShape.shape(9f / 16), 0f)
        assertEquals(1f, MilestoneSnapShape.shape(Float.NaN), 0f)
    }

    @Test
    fun `a polaroid keeps within its box, narrow for a portrait, wide for a landscape`() {
        val portrait = MilestoneSnapShape.layout(height = 60f, ratio = 9f / 16)
        val landscape = MilestoneSnapShape.layout(height = 60f, ratio = 16f / 9)
        assertEquals(tall, portrait.windowWidth / portrait.windowHeight, 1e-5f)
        assertEquals(wide, landscape.windowWidth / landscape.windowHeight, 1e-5f)
        assertTrue(portrait.cardWidth < landscape.cardWidth)
        listOf(portrait, landscape).forEach {
            assertTrue(it.cardHeight <= 60f + 1e-4f)
            assertTrue(it.cardWidth <= 60f * 1.15f + 1e-4f)
        }
        val pending = MilestoneSnapShape.layout(height = 60f, ratio = Float.NaN)
        assertEquals(pending.windowWidth, pending.windowHeight, 1e-5f)
    }
}
