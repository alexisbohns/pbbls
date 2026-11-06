package app.pbbls.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What "Record another" opens, per origin. */
class CompleteOriginTest {
    @Test
    fun `each origin restarts the kind of capture that began it, and the card has none`() {
        assertEquals(PebblesKey.Capture(), CompleteOrigin.CAPTURE.restart())
        assertEquals(PebblesKey.Capture(chainsComplete = true), CompleteOrigin.HOME_NEW_PEBBLE.restart())
        assertEquals(PebblesKey.Capture(startsAtMomentPage = true), CompleteOrigin.QUICK_MOMENT.restart())
        assertNull(CompleteOrigin.CARD.restart())
    }
}
