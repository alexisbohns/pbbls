package app.pbbls.android.features.glyph.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SlideMath] — the iOS slide geometry: travel excludes the thumb; confirm at 90% of travel. */
class SlideMathTest {
    @Test
    fun `travel is track minus thumb, floored at one`() {
        assertEquals(244f, SlideMath.travel(trackWidth = 300f, thumb = 56f))
        assertEquals(1f, SlideMath.travel(trackWidth = 40f, thumb = 56f))
    }

    @Test
    fun `progress clamps the drag into the travel range`() {
        assertEquals(0f, SlideMath.progress(dragX = -10f, travel = 200f))
        assertEquals(0.5f, SlideMath.progress(dragX = 100f, travel = 200f))
        assertEquals(1f, SlideMath.progress(dragX = 500f, travel = 200f))
    }

    @Test
    fun `confirm threshold is ninety percent of travel`() {
        assertFalse(SlideMath.isConfirmed(0.89f))
        assertTrue(SlideMath.isConfirmed(0.9f))
        assertTrue(SlideMath.isConfirmed(1f))
    }
}
