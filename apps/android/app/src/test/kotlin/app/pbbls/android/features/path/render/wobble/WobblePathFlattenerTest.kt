package app.pbbls.android.features.path.render.wobble

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.features.path.render.Affine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Mirrors iOS `WobblePathFlattenerTests.swift`. */
class WobblePathFlattenerTest {
    private fun nodes(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()

    @Test
    fun `straight lines gain interior vertices so they can bend`() {
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 L10,0"), step = 2.0)
        assertEquals(1, polylines.size)
        val points = polylines[0].points
        assertEquals(6, points.size) // 0, 2, 4, 6, 8, 10
        assertEquals(WobblePoint(0.0, 0.0), points.first())
        assertEquals(WobblePoint(10.0, 0.0), points.last())
        assertFalse(polylines[0].isClosed)
    }

    @Test
    fun `cubic chords stay near the step`() {
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 C30,60 70,60 100,0"), step = 2.0)
        val points = polylines[0].points
        assertTrue(points.size > 10)
        for (i in 1 until points.size) {
            val chord = hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            assertTrue("chord $i too long: $chord", chord <= 3) // step × 1.5 headroom
        }
        assertEquals(WobblePoint(100.0, 0.0), points.last())
    }

    @Test
    fun `Z closes the ring without duplicating the start point`() {
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 L10,0 L10,10 L0,10 Z"), step = 2.0)
        assertEquals(1, polylines.size)
        val polyline = polylines[0]
        assertTrue(polyline.isClosed)
        assertEquals(WobblePoint(0.0, 0.0), polyline.points.first())
        assertTrue(polyline.points.last() != polyline.points.first())
        // 4 sides × 5 chords, minus the deduplicated ring-closing point.
        assertEquals(20, polyline.points.size)
    }

    @Test
    fun `multiple subpaths flatten to multiple polylines`() {
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 L4,0 M0,10 L4,10"), step = 2.0)
        assertEquals(2, polylines.size)
        assertFalse(polylines[0].isClosed)
        assertFalse(polylines[1].isClosed)
    }

    @Test
    fun `a single-tap carve dot survives as a one-point polyline`() {
        // Carve dots serialize as "M p L p" (one-point stroke).
        val polylines = WobblePathFlattener.flatten(nodes("M5,5 L5,5"), step = 2.0)
        assertEquals(1, polylines.size)
        assertEquals(listOf(WobblePoint(5.0, 5.0)), polylines[0].points)
    }

    @Test
    fun `quad curves flatten through the control point's pull`() {
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 Q10,10 20,0"), step = 2.0)
        val points = polylines[0].points
        assertTrue(points.size > 5)
        // The curve's apex (t = 0.5) passes through y = 5.
        val apexY = points.maxOf { it.y }
        assertTrue("apex $apexY too far from 5", abs(apexY - 5) < 1)
    }

    @Test
    fun `a transform is baked in before subdivision`() {
        // Android adaptation: iOS pre-transforms the CGPath in the model; here
        // the affine rides into flatten(). scale(2) doubles the geometry, so
        // the 10-unit line becomes 20 units → 11 points at step 2.
        val transform = Affine(2f, 0f, 0f, 2f, 1f, 0f)
        val polylines = WobblePathFlattener.flatten(nodes("M0,0 L10,0"), step = 2.0, transform = transform)
        val points = polylines[0].points
        assertEquals(11, points.size)
        assertEquals(WobblePoint(1.0, 0.0), points.first())
        assertEquals(WobblePoint(21.0, 0.0), points.last())
    }
}
