package app.pbbls.android.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who draws the celebrations: the root, or an open sheet (#940). */
class SheetOverlaySlotTest {
    @Test
    fun `the root hosts them while no sheet is open`() {
        assertFalse(SheetOverlaySlot().isHostedBySheet)
    }

    @Test
    fun `an open sheet hosts them until it is hidden`() {
        val slot = SheetOverlaySlot()
        slot.onSheetShown()
        assertTrue(slot.isHostedBySheet)

        slot.onSheetHidden()
        assertFalse(slot.isHostedBySheet)
    }

    @Test
    fun `a sheet entering before the last one leaves keeps them in a sheet`() {
        val slot = SheetOverlaySlot()
        slot.onSheetShown()
        slot.onSheetShown()
        slot.onSheetHidden()

        assertTrue(slot.isHostedBySheet)
    }
}
