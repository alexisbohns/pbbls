package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `IntroMotionTests.eases` and `.pop` (#1241). */
class RevealMotionTest {
    @Test
    fun `the eases run from 0 to 1, and the inverse undoes the in-out`() {
        assertEquals(0.0, RevealMotion.easeOut(0.0), 0.0)
        assertEquals(1.0, RevealMotion.easeOut(1.0), 0.0)
        assertEquals(0.0, RevealMotion.easeInOut(0.0), 0.0)
        assertEquals(1.0, RevealMotion.easeInOut(1.0), 1e-15)
        for (i in 0..20) {
            val x = i / 20.0
            assertEquals(x, RevealMotion.easeInOutInverse(RevealMotion.easeInOut(x)), 1e-9)
        }
    }

    @Test
    fun `a pop grows from 0_4 to 1 past about 1_08, opaque after its first 120 ms`() {
        assertEquals(0.4, RevealMotion.popScale(0.0), 1e-12)
        assertEquals(1.0, RevealMotion.popScale(1.0), 0.0)
        assertEquals(1.079, RevealMotion.popScale(5.0 / 9.0), 0.001)
        assertEquals(0.0, RevealMotion.popOpacity(0.0), 0.0)
        assertEquals(1.0, RevealMotion.popOpacity(0.12 / 0.22), 1e-12)
    }
}
