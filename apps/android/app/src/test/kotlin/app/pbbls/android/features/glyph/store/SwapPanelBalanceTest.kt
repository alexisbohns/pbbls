package app.pbbls.android.features.glyph.store

import app.pbbls.android.core.model.BuyGlyphResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The balance the swap panel shows (#940). The composable cannot run on the
 * JVM here (no Robolectric), so its one rule lives in [swapPanelBalance].
 */
class SwapPanelBalanceTest {
    /**
     * The panel used to copy the balance once, at first draw. A detail opened
     * before the stats load landed (a restore, a fast tap on cold start) then
     * stayed at 0 and showed "Not enough karma" for good.
     */
    @Test
    fun `before a purchase the panel follows the live balance`() {
        assertEquals(0, swapPanelBalance(landed = null, live = 0))
        assertEquals(42, swapPanelBalance(landed = null, live = 42))
    }

    @Test
    fun `after a purchase the panel shows the server's balance`() {
        assertEquals(32, swapPanelBalance(landed = BuyGlyphResult(entitlementId = "e1", balance = 32), live = 42))
    }
}
