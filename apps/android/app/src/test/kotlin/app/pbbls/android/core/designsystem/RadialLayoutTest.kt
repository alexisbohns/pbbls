package app.pbbls.android.core.designsystem

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class RadialLayoutTest {
    @Test
    fun `slot 0 sits half the gap past the start, clockwise`() {
        val ring = RadialLayout(31, startDeg = -90f, gapSlots = 1.5f)
        val step = 360f / 32.5f
        assertEquals(-90f + 0.75f * step, ring.angleDeg(0), 1e-4f)
        assertEquals(-90f + 30.75f * step, ring.angleDeg(30), 1e-4f)
        // The last day stops short of the first by the gap and one step.
        assertEquals(360f - 1.5f * step, ring.angleDeg(30) - ring.angleDeg(0) + step, 1e-3f)
    }

    @Test
    fun `counter-clockwise turns the other way`() {
        val ring = RadialLayout(4, startDeg = 0f, clockwise = false)
        assertEquals(-90f, ring.angleDeg(1), 1e-4f)
    }

    @Test
    fun `the dial puts noon on top and six in the morning on the left`() {
        val dial = RadialLayout.continuous(24, top = 12f)
        assertEquals(-90f, dial.angleDeg(12f), 1e-4f)
        assertEquals(-180f, dial.angleDeg(6f), 1e-4f)
        val left = dial.point(6f, Offset(50f, 50f), 10f)
        assertEquals(40f, left.x, 1e-3f)
        assertEquals(50f, left.y, 1e-3f)
    }

    @Test
    fun `a gem rotation is a fractional slot`() {
        val gem = RadialLayout(7)
        assertEquals(gem.angleDeg(3f), gem.angleDeg(1f + 2f), 0f)
        assertEquals(-90f, gem.angleDeg(0), 1e-4f)
    }
}
