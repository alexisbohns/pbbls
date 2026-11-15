package app.pbbls.android.core.model.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ported from iOS `CropGeometryTests` (#1659): the oracle for "no empty corners, ever". */
class CropGeometryTest {
    private val portrait = CropSize(1536.0, 2048.0) // 3:4
    private val landscape = CropSize(2048.0, 1152.0) // 16:9

    /** Every window corner, taken back into the image, lies inside it. */
    private fun covers(
        g: CropGeometry,
        tolerance: Double = 0.01,
    ): Boolean =
        g.windowCornersInImage().all { p ->
            abs(p.x) <= g.imageSize.width / 2 + tolerance && abs(p.y) <= g.imageSize.height / 2 + tolerance
        }

    @Test
    fun `opens on the closest format, centred, at the smallest covering scale`() {
        val g = CropGeometry.initial(portrait)
        assertEquals(CropRatio.THREE_FOUR, g.ratio)
        assertEquals(CropPoint.Zero, g.focus)
        assertEquals(0.0, g.angle, 0.0)
        assertEquals(0, g.quarterTurns)
        assertEquals(g.minScale, g.scale, 1e-9)
        assertEquals(1.0 / 2048, g.scale, 1e-9) // window height 1 == image height
        assertTrue(covers(g))
    }

    @Test
    fun `min scale covers exactly - a hair less leaves a corner out`() {
        for (angle in listOf(-45.0, -17.0, 0.0, 9.5, 33.0, 45.0)) {
            for (size in listOf(portrait, landscape)) {
                for (ratio in CropRatio.entries) {
                    for (turns in 0 until 4) {
                        val base = CropGeometry.initial(size).copy(ratio = ratio, quarterTurns = turns, angle = angle)
                        val exact = base.copy(scale = base.minScale)
                        assertTrue("$size $ratio $turns $angle", covers(exact))
                        assertFalse("$size $ratio $turns $angle", covers(exact.copy(scale = exact.minScale * 0.999)))
                    }
                }
            }
        }
    }

    @Test
    fun `normalising always covers, whatever the pan, zoom, turn and format`() {
        val angles = listOf(-45.0, -30.0, -7.5, 0.0, 3.0, 22.0, 45.0)
        val focuses = listOf(CropPoint.Zero, CropPoint(5000.0, 0.0), CropPoint(-900.0, 1400.0), CropPoint(300.0, -300.0))
        val scales = listOf(0.00001, 0.0004, 0.002, 1.0)
        for (size in listOf(portrait, landscape)) {
            for (ratio in CropRatio.entries) {
                for (turns in 0 until 4) {
                    for (angle in angles) {
                        for (focus in focuses) {
                            for (scale in scales) {
                                val g = CropGeometry(size, ratio, turns, angle, scale, focus).normalized()
                                val label = "$size $ratio $turns $angle $focus $scale"
                                assertTrue(label, covers(g))
                                assertTrue(label, g.scale <= g.maxScale + 1e-12)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `a focus already inside the bounds is left where it is`() {
        val g = CropGeometry.initial(landscape).let { it.copy(ratio = CropRatio.SQUARE) }
        val inside = g.copy(scale = g.minScale, focus = CropPoint(120.0, 0.0))
        assertEquals(CropPoint(120.0, 0.0), inside.normalized().focus)
    }

    @Test
    fun `the angle is clamped to 45 and the turns wrap`() {
        val g = CropGeometry(portrait, CropRatio.SQUARE, quarterTurns = 5, angle = 80.0, scale = 1.0, focus = CropPoint.Zero).normalized()
        assertEquals(45.0, g.angle, 0.0)
        assertEquals(1, g.quarterTurns)
        val negative = CropGeometry(portrait, CropRatio.SQUARE, quarterTurns = -1, angle = -80.0, scale = 1.0, focus = CropPoint.Zero)
        assertEquals(3, negative.normalized().quarterTurns)
        assertEquals(-45.0, negative.normalized().angle, 0.0)
    }

    @Test
    fun `turning a quarter flips the format to its pair and keeps the focus`() {
        val g = CropGeometry.initial(portrait).copy(focus = CropPoint(0.0, 100.0))
        val turned = g.turnedQuarter()
        assertEquals(1, turned.quarterTurns)
        assertEquals(CropRatio.FOUR_THREE, turned.ratio)
        // Here the turned window spans the whole photo, so the focus is pulled to the centre.
        assertEquals(0.0, turned.focus.x, 1e-9)
        assertEquals(0.0, turned.focus.y, 1e-9)
        assertEquals(turned, turned.normalized())
    }

    @Test
    fun `turning a quarter on a zoomed-in photo leaves the focus where it was`() {
        val start = CropGeometry.initial(portrait)
        val g = start.copy(scale = start.minScale * 3, focus = CropPoint(40.0, 60.0))
        val turned = g.turnedQuarter()
        assertEquals(CropPoint(40.0, 60.0), turned.focus)
        assertEquals(1, turned.quarterTurns)
    }

    @Test
    fun `changing the angle raises the scale only as far as coverage needs`() {
        val start = CropGeometry.initial(portrait)
        val g = start.copy(scale = start.minScale * 3)
        val tilted = g.withAngle(10.0)
        assertEquals(g.scale, tilted.scale, 0.0) // already covering: untouched
        val fromMin = CropGeometry.initial(portrait).withAngle(10.0)
        assertEquals(fromMin.minScale, fromMin.scale, 1e-12)
    }

    @Test
    fun `dragging right moves the photo right - the focus moves left in the image`() {
        val g = CropGeometry.initial(landscape) // 16:9 → 4:3, room to pan sideways
        val moved = g.panned(CropPoint(0.1, 0.0))
        assertTrue(moved.focus.x < 0)
        assertEquals(0.0, moved.focus.y, 1e-9)
    }

    @Test
    fun `the rubber band shows part of an overshoot, never all of it`() {
        val far = CropGeometry.initial(landscape).panned(CropPoint(10.0, 0.0))
        val banded = far.rubberBanded()
        val clamped = far.normalized()
        assertTrue(abs(banded.focus.x) > abs(clamped.focus.x))
        assertTrue(abs(banded.focus.x) < abs(far.focus.x))
    }

    @Test
    fun `the window offset puts the focus at the window centre`() {
        val start = CropGeometry.initial(landscape)
        val g = start.copy(angle = 20.0, scale = start.minScale * 2, focus = CropPoint(200.0, -50.0))
        // The focus point, drawn: R(θ)·(focus·scale) + offset == 0.
        val drawn = CropPoint(g.focus.x * g.scale, g.focus.y * g.scale).rotated(g.theta)
        assertEquals(0.0, drawn.x + g.windowOffset.x, 1e-9)
        assertEquals(0.0, drawn.y + g.windowOffset.y, 1e-9)
    }

    @Test
    fun `a positive angle turns clockwise on a y-down screen`() {
        // +x turned a quarter clockwise in y-down space points down (+y).
        val turned = CropPoint(1.0, 0.0).rotated(Math.toRadians(90.0))
        assertEquals(0.0, turned.x, 1e-12)
        assertEquals(1.0, turned.y, 1e-12)
        assertNotEquals(CropPoint(1.0, 0.0), turned)
    }
}
