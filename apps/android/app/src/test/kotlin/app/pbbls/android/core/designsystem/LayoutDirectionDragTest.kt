package app.pbbls.android.core.designsystem

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Physical pointer x → logical (start-to-end) x, so drag math is written once for LTR and RTL. */
class LayoutDirectionDragTest {
    @Test
    fun `a delta keeps its sign in LTR and flips in RTL`() {
        assertEquals(12f, LayoutDirection.Ltr.logicalDelta(12f))
        assertEquals(-12f, LayoutDirection.Rtl.logicalDelta(12f))
    }

    @Test
    fun `distance from the start edge is x in LTR and width minus x in RTL`() {
        assertEquals(20f, LayoutDirection.Ltr.distanceFromStart(x = 20f, width = 300f))
        assertEquals(280f, LayoutDirection.Rtl.distanceFromStart(x = 20f, width = 300f))
    }
}
