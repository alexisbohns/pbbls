package app.pbbls.android.features.path.render.wobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Mirrors iOS `WobbleOutlineBuilderTests.swift`. */
class WobbleOutlineBuilderTest {
    /** Params that displace nothing — isolates outline construction from noise. */
    private val identityParams = WobbleParams(amplitude = 0.0, frequency = 0.024, octaves = 1, flattenStep = 2.0)
    private val noise = SVGTurbulence(WobbleParams.SEED)

    @Test
    fun `open polyline yields one capped contour`() {
        val polyline = WobblePolyline(listOf(WobblePoint(0.0, 0.0), WobblePoint(10.0, 0.0)), isClosed = false)
        val contours = WobbleOutlineBuilder.contours(polyline, halfWidth = 3.0)
        assertEquals(1, contours.size)
        // 2n side points + 2 caps × 5 interior points (6-segment semicircles).
        assertEquals(2 * 2 + 2 * 5, contours[0].size)
    }

    @Test
    fun `closed polyline yields two opposite-winding rings`() {
        val square =
            listOf(
                WobblePoint(0.0, 0.0),
                WobblePoint(10.0, 0.0),
                WobblePoint(10.0, 10.0),
                WobblePoint(0.0, 10.0),
            )
        val contours = WobbleOutlineBuilder.contours(WobblePolyline(square, isClosed = true), halfWidth = 2.0)
        assertEquals(2, contours.size)
        assertTrue(signedArea(contours[0]) * signedArea(contours[1]) < 0)
    }

    @Test
    fun `dot becomes a filled circle of halfWidth radius`() {
        val contours =
            WobbleOutlineBuilder.contours(
                WobblePolyline(listOf(WobblePoint(5.0, 5.0)), isClosed = false),
                halfWidth = 3.0,
            )
        assertEquals(1, contours.size)
        assertEquals(12, contours[0].size)
        for (point in contours[0]) {
            assertTrue(abs(hypot(point.x - 5, point.y - 5) - 3) < 1e-9)
        }
    }

    @Test
    fun `undisplaced ink contains the centerline and excludes the outside`() {
        val polyline =
            WobblePolyline((0..10).map { WobblePoint(it * 2.0, 5.0) }, isClosed = false)
        val art = WobbleOutlineBuilder.art(listOf(polyline), halfWidth = 3.0, params = identityParams, noise = noise)
        assertTrue(windingContains(art.ink, 10.0, 5.0))
        assertFalse(windingContains(art.ink, 10.0, 20.0))
    }

    @Test
    fun `closed ring ink is an annulus - band is full, hole is empty`() {
        val circle =
            (0 until 60).map { i ->
                val angle = 2 * Math.PI * i / 60
                WobblePoint(10 * cos(angle), 10 * sin(angle))
            }
        val art =
            WobbleOutlineBuilder.art(
                listOf(WobblePolyline(circle, isClosed = true)),
                halfWidth = 2.0,
                params = identityParams,
                noise = noise,
            )
        assertTrue(windingContains(art.ink, 10.0, 0.0))
        assertFalse(windingContains(art.ink, 0.0, 0.0))
    }

    @Test
    fun `wobbled ink actually breathes - contour diverges from constant offset`() {
        // A long horizontal centerline through the 200-box, canonical params:
        // displaced contour points must not all sit at a constant ±hw offset,
        // otherwise the "leaky" quality is lost.
        val polyline =
            WobblePolyline((0..100).map { WobblePoint(it * 2.0, 100.0) }, isClosed = false)
        val art =
            WobbleOutlineBuilder.art(
                listOf(polyline),
                halfWidth = 3.0,
                params = WobbleParams.CANONICAL,
                noise = noise,
            )
        val ys = art.ink.flatten().map { it.y }
        val height = ys.max() - ys.min()
        // With amplitude 18, the ink's vertical extent must clearly exceed the
        // rigid 6-unit band a constant-width stroke would produce.
        assertTrue("ink bounding box height $height — wobble looks inert", height > 8)
    }

    @Test
    fun `determinism - same input, identical geometry`() {
        val polyline =
            WobblePolyline(
                listOf(WobblePoint(0.0, 0.0), WobblePoint(20.0, 14.0), WobblePoint(40.0, 3.0)),
                isClosed = false,
            )
        val first = WobbleOutlineBuilder.art(listOf(polyline), 3.0, WobbleParams.CANONICAL, noise)
        val second = WobbleOutlineBuilder.art(listOf(polyline), 3.0, WobbleParams.CANONICAL, noise)
        assertEquals(first.ink, second.ink)
        assertEquals(first.centerline, second.centerline)
    }

    private fun signedArea(points: List<WobblePoint>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2
    }

    /**
     * Nonzero-winding point-in-polygon over the ink's closed contours — the
     * JVM stand-in for iOS `CGPath.contains(_:using: .winding)`.
     */
    private fun windingContains(
        contours: List<List<WobblePoint>>,
        x: Double,
        y: Double,
    ): Boolean {
        var winding = 0
        for (contour in contours) {
            for (i in contour.indices) {
                val a = contour[i]
                val b = contour[(i + 1) % contour.size]
                val cross = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
                if (a.y <= y) {
                    if (b.y > y && cross > 0) winding += 1
                } else {
                    if (b.y <= y && cross < 0) winding -= 1
                }
            }
        }
        return winding != 0
    }
}
