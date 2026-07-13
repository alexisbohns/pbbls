package app.pbbls.android.features.path.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the outline stroke constant and the pure transform algebra that
 * [parsePebbleSvg] flattens with. Mirrors iOS `PebbleStrokeTests`.
 */
class PebbleStrokeTest {
    @Test
    fun `outline width is six`() {
        assertEquals(6f, PebbleStroke.OUTLINE_WIDTH, 0f)
    }

    @Test
    fun `parseTransform composes translate then scale`() {
        val t = parseTransform("translate(30, 30) scale(0.7)")
        // A point p is scaled first, then translated: p → (0.7·x + 30, 0.7·y + 30).
        assertEquals(Affine(0.7f, 0f, 0f, 0.7f, 30f, 30f), t)
    }

    @Test
    fun `parseTransform reads a lone scale and a two-arg scale`() {
        assertEquals(Affine.scale(0.5f, 0.5f), parseTransform("scale(0.5)"))
        assertEquals(Affine.scale(0.5f, 0.25f), parseTransform("scale(0.5, 0.25)"))
    }

    @Test
    fun `parseTransform is identity for unrecognized input`() {
        assertEquals(Affine.IDENTITY, parseTransform("rotate(45)"))
        assertEquals(Affine.IDENTITY, parseTransform(""))
    }

    @Test
    fun `concat applies the argument first then the receiver`() {
        // translate(10,20) ∘ scale(2): p → translate(scale(p)) = (2·x + 10, 2·y + 20).
        val t = Affine.translate(10f, 20f).concat(Affine.scale(2f, 2f))
        assertEquals(Affine(2f, 0f, 0f, 2f, 10f, 20f), t)
    }

    @Test
    fun `identity is a concat no-op on both sides`() {
        val t = Affine(0.7f, 0f, 0f, 0.7f, 30f, 30f)
        assertEquals(t, t.concat(Affine.IDENTITY))
        assertEquals(t, Affine.IDENTITY.concat(t))
    }
}
