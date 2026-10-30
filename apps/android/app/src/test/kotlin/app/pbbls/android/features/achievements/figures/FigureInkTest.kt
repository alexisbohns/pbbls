package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.graphics.Color
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.ui.render.stone.StoneTones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The board's colour rules (#1307): `famHue`, `tx` and the rosace's `tone`. */
class FigureInkTest {
    private val joy =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#A15C08FF",
                secondaryHex = "#CF8C39FF",
                lightHex = "#FAF6EAFF",
                surfaceHex = "#A15C081A",
                darkHex = "#201202FF",
                shadedHex = "#714006FF",
            ),
        )

    @Test
    fun `a family's hue is its primary on paper and its secondary at night`() {
        assertEquals(joy.primary, joy.hue(night = false))
        assertEquals(joy.secondary, joy.hue(night = true))
    }

    @Test
    fun `shame's name reads in its shaded tone on paper`() {
        assertEquals(joy.shaded, familyLabelColor("shame", joy, night = false))
        assertEquals(joy.primary, familyLabelColor("joy", joy, night = false))
        assertEquals(joy.secondary, familyLabelColor("shame", joy, night = true))
    }

    @Test
    fun `a family's first seat at night is its secondary, the run deepens, the soft star is paler`() {
        val stock = joy.light
        val first = seatTone(joy, index = 0, familySize = 5, night = true, stock = stock, soft = false)
        assertEquals(joy.secondary, first)
        val last = seatTone(joy, index = 4, familySize = 5, night = true, stock = stock, soft = false)
        assertNotEquals(first, last)
        assertNotEquals(last, seatTone(joy, index = 4, familySize = 5, night = true, stock = stock, soft = true))
    }

    @Test
    fun `an unresolved family's soft star is its ink softened toward the stock, not solid ink`() {
        val ink = Color.Black
        val stock = Color.White
        val soft = softTone(ink, stock)
        assertNotEquals(ink, soft)
        assertNotEquals(stock, soft)
        assertEquals(StoneTones.mixOklab(stock, 0.38f, ink), soft)
    }
}
