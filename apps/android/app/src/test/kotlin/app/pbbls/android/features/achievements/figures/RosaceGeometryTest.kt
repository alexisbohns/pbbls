package app.pbbls.android.features.achievements.figures

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** The rosace's cut (#1307): the board's `gem` radii, seat angles, kites, stars and labels. */
class RosaceGeometryTest {
    private val g = RosaceGeometry(size = 270f, labels = true, seats = 38)

    private fun r(p: Offset) = hypot((p.x - g.centre).toDouble(), (p.y - g.centre).toDouble()).toFloat()

    @Test
    fun `the radii follow the board, with labels shrinking the stone`() {
        assertEquals(270f * 0.345f, g.radius, 1e-3f)
        assertEquals(g.radius * 0.85f, g.inner, 1e-3f)
        assertEquals(g.radius * 0.47f, g.core, 1e-3f)
        assertEquals(200f * 0.44f, RosaceGeometry(200f, labels = false, seats = 38).radius, 1e-3f)
    }

    @Test
    fun `seat zero sits two and a half steps before twelve o'clock`() {
        assertEquals(-PI / 2 - 2.5 * 2 * PI / 38, g.angle(0), 1e-9)
    }

    @Test
    fun `a kite reaches the rim at its seat and the core between, a star joins two seats`() {
        val kite = g.kite(3)
        assertEquals(4, kite.size)
        assertEquals(g.radius, r(kite[1]), 1e-3f)
        assertEquals(g.core, r(kite[3]), 1e-3f)
        val star = g.star(3)
        assertEquals(3, star.size)
        assertEquals(g.core, r(star[0]), 1e-3f)
        assertEquals(g.core, r(star[2]), 1e-3f)
        assertEquals(g.inner, r(star[1]), 1e-3f)
    }

    @Test
    fun `the silhouette has two points per seat, labels sit 22 past the rim`() {
        assertEquals(76, g.silhouette().size)
        assertEquals(g.radius + 22f, r(g.label(start = 0, size = 9)), 1e-3f)
        assertEquals(g.radius + 6.5f, r(g.freshDot(5)), 1e-3f)
    }
}
