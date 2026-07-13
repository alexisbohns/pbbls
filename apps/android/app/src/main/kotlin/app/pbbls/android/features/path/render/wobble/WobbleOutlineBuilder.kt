package app.pbbls.android.features.path.render.wobble

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The wobbled render of one artwork piece, as pure geometry (no Compose or
 * `android.graphics` types, so the whole pipeline is JVM unit-testable —
 * same rule as `PebbleSvgModel`). `WobbleShapes.kt` converts it to a Compose
 * [androidx.compose.ui.graphics.Path] at the view layer.
 *
 * [ink] is the leaky filled outline: closed contours, filled together under
 * the nonzero winding rule. [centerline] is the displaced centerline the
 * future appear animation's reveal mask must stroke along (fat trimmed
 * stroke, mirroring iOS `PebbleAnimatedRenderView` — not wired up yet;
 * Android has no draw-on animation).
 */
internal class WobbleArt(
    val ink: List<List<WobblePoint>>,
    val centerline: List<WobblePolyline>,
)

/**
 * Converts flattened centerline polylines into wobbled filled outlines,
 * porting iOS `WobbleOutlineBuilder.swift` (itself the playground's
 * baked-export algorithm): offset both stroke edges from the centerline
 * first, then displace every contour point independently. The independent
 * displacement is what makes the width breathe ("leaky") — a stroked wobbled
 * centerline would stay constant-width.
 */
internal object WobbleOutlineBuilder {
    /**
     * End caps are semicircles of [CAP_SEGMENTS] arcs (CAP_SEGMENTS − 1
     * interior points), matching the playground.
     */
    private const val CAP_SEGMENTS = 6

    /**
     * A zero-length polyline (single-tap carve dot) becomes a circle with
     * this many segments. The playground drops dots; we keep them visible,
     * matching the current renderer's round-cap dot.
     */
    private const val DOT_SEGMENTS = 12

    fun art(
        polylines: List<WobblePolyline>,
        halfWidth: Double,
        params: WobbleParams,
        noise: SVGTurbulence,
    ): WobbleArt {
        val ink = mutableListOf<List<WobblePoint>>()
        val centerline = mutableListOf<WobblePolyline>()
        for (polyline in polylines) {
            for (contour in contours(polyline, halfWidth)) {
                if (contour.size <= 2) continue
                ink.add(contour.map { params.displace(it, noise) })
            }
            centerline.add(displacedCenterline(polyline, params, noise))
        }
        return WobbleArt(ink, centerline)
    }

    /**
     * Un-displaced outline contours for one polyline. Internal so tests can
     * assert point counts and winding without decoding a displaced path.
     */
    fun contours(
        polyline: WobblePolyline,
        halfWidth: Double,
    ): List<List<WobblePoint>> {
        val points = polyline.points
        val count = points.size

        if (count == 1) {
            val cx = points[0].x
            val cy = points[0].y
            val circle =
                (0 until DOT_SEGMENTS).map { i ->
                    val angle = 2 * Math.PI * i / DOT_SEGMENTS
                    WobblePoint(cx + halfWidth * cos(angle), cy + halfWidth * sin(angle))
                }
            return listOf(circle)
        }

        val normals = normals(points, polyline.isClosed)
        val left =
            (0 until count).map { i ->
                WobblePoint(points[i].x + normals[i].x * halfWidth, points[i].y + normals[i].y * halfWidth)
            }
        val right =
            (0 until count).map { i ->
                WobblePoint(points[i].x - normals[i].x * halfWidth, points[i].y - normals[i].y * halfWidth)
            }

        if (polyline.isClosed) {
            // Outer ring + reversed inner ring: opposite windings make the
            // pair render as an annulus under nonzero fill.
            return listOf(left, right.reversed())
        }

        val contour = left.toMutableList()
        appendCapArc(
            contour = contour,
            center = points[count - 1],
            from = left[count - 1],
            halfWidth = halfWidth,
            tangentX = points[count - 1].x - points[count - 2].x,
            tangentY = points[count - 1].y - points[count - 2].y,
        )
        contour.addAll(right.reversed())
        appendCapArc(
            contour = contour,
            center = points[0],
            from = right[0],
            halfWidth = halfWidth,
            tangentX = points[0].x - points[1].x,
            tangentY = points[0].y - points[1].y,
        )
        return listOf(contour)
    }

    // ── Pieces ─────────────────────────────────────────────────

    /**
     * Per-point normals from neighbor tangents; endpoints use their single
     * adjacent segment, closed rings wrap cyclically.
     */
    private fun normals(
        points: List<WobblePoint>,
        closed: Boolean,
    ): List<WobblePoint> {
        val count = points.size
        return (0 until count).map { i ->
            val prev = if (closed) points[(i - 1 + count) % count] else points[maxOf(0, i - 1)]
            val next = if (closed) points[(i + 1) % count] else points[minOf(count - 1, i + 1)]
            val tx = next.x - prev.x
            val ty = next.y - prev.y
            val rawLength = hypot(tx, ty)
            val length = if (rawLength == 0.0) 1.0 else rawLength // playground: `|| 1`
            WobblePoint(-ty / length, tx / length)
        }
    }

    /**
     * Appends the interior points of a semicircular end cap around [center],
     * starting from [from] and bulging along the tangent direction.
     */
    private fun appendCapArc(
        contour: MutableList<WobblePoint>,
        center: WobblePoint,
        from: WobblePoint,
        halfWidth: Double,
        tangentX: Double,
        tangentY: Double,
    ) {
        val startAngle = atan2(from.y - center.y, from.x - center.x)
        val midAngle = startAngle + Math.PI / 2
        val direction = if (cos(midAngle) * tangentX + sin(midAngle) * tangentY >= 0) 1.0 else -1.0
        for (step in 1 until CAP_SEGMENTS) {
            val angle = startAngle + direction * (Math.PI * step / CAP_SEGMENTS)
            contour.add(WobblePoint(center.x + halfWidth * cos(angle), center.y + halfWidth * sin(angle)))
        }
    }

    /**
     * The displaced centerline mirrors the input subpath structure so a
     * future reveal mask's trim progresses across subpaths in the same order
     * as a stroke draw-on would. A one-point polyline stays one point — the
     * mask stroke's round cap is what will reveal the dot.
     */
    private fun displacedCenterline(
        polyline: WobblePolyline,
        params: WobbleParams,
        noise: SVGTurbulence,
    ): WobblePolyline = WobblePolyline(polyline.points.map { params.displace(it, noise) }, polyline.isClosed)
}
