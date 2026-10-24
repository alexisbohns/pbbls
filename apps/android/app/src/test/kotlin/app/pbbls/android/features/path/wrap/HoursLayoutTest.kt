package app.pbbls.android.features.path.wrap

import app.pbbls.android.features.path.wrap.month.HoursLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class HoursLayoutTest {
    @Test
    fun `the wait runs clockwise across midnight`() {
        assertEquals(2.5, HoursLayout.forward(22.5, 1.0), 1e-9)
        assertEquals(0.5, HoursLayout.forward(10.0, 10.5), 1e-9)
        assertEquals(0.0, HoursLayout.forward(9.0, 9.0), 1e-9)
    }

    @Test
    fun `marks that would touch step out a lane, across midnight too`() {
        // One hour is 20 px on every lane; marks are 10 px wide.
        val lanes = HoursLayout.lanes(listOf(10.0, 10.2, 15.0, 23.9, 0.1), List(5) { 10f }) { 20f }
        assertEquals(listOf(0, 1, 0, 1, 0), lanes)
    }
}
