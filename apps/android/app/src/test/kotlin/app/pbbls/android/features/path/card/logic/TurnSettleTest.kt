package app.pbbls.android.features.path.card.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnSettleTest {
    @Test
    fun `a release past the half completes the turn`() {
        assertEquals(1f, TurnSettle.target(progress = 0.6f, velocityPerSecond = 0f))
    }

    @Test
    fun `a release short of the half reverts`() {
        assertEquals(0f, TurnSettle.target(progress = 0.4f, velocityPerSecond = 0f))
    }

    @Test
    fun `a flick carries the turn whatever the progress`() {
        assertEquals(1f, TurnSettle.target(progress = 0.1f, velocityPerSecond = 2.5f))
        assertEquals(0f, TurnSettle.target(progress = 0.9f, velocityPerSecond = -2.5f))
    }

    @Test
    fun `progress maps drag distance against the card width, clamped`() {
        assertEquals(0.5f, TurnSettle.progress(dragPx = -150f, widthPx = 300f), 0.001f)
        assertEquals(1f, TurnSettle.progress(dragPx = -900f, widthPx = 300f), 0.001f)
        assertEquals(0f, TurnSettle.progress(dragPx = 120f, widthPx = 300f), 0.001f)
    }

    @Test
    fun `a card with no width does not turn`() {
        assertEquals(0f, TurnSettle.progress(dragPx = -50f, widthPx = 0f), 0.001f)
    }
}
