package app.pbbls.android.features.path.render.wobble

import androidx.compose.ui.graphics.vector.PathNode
import app.pbbls.android.features.path.render.Affine
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A 2-D point in the wobble pipeline. Double-precision — the golden fixtures assert to 1e-9. */
internal data class WobblePoint(
    val x: Double,
    val y: Double,
)

/** A flattened subpath: dense points ready for displacement. */
internal data class WobblePolyline(
    val points: List<WobblePoint>,
    val isClosed: Boolean,
)

/**
 * Flattens parsed SVG path nodes into polylines whose chords are ≤ ~`step`
 * units, so the noise displacement reads as a smooth wobble rather than a
 * kinked polygon (issue #555 §2.3 step 1) — mirrors iOS
 * `WobblePathFlattener.swift`. Straight segments are subdivided too — a long
 * straight line needs interior vertices or it cannot bend.
 *
 * Two Android adaptations of the iOS `CGPath` walk:
 * - Input is Compose's [PathNode] list (`PathParser().parsePathString(d)
 *   .toNodes()`), so relative/shorthand/arc commands are normalized here to
 *   the absolute move/line/quad/cubic/close primitives `CGPath` enumeration
 *   hands iOS.
 * - An optional [Affine] bakes an enclosing-group transform into the geometry
 *   before subdivision (chords are measured post-transform), standing in for
 *   iOS `PebbleSVGModel` pre-transforming its `combinedPath`.
 */
internal object WobblePathFlattener {
    private const val DUPLICATE_EPSILON = 1e-6

    fun flatten(
        nodes: List<PathNode>,
        step: Double,
        transform: Affine = Affine.IDENTITY,
    ): List<WobblePolyline> {
        val walker = NodeWalker(step, transform)
        for (node in nodes) {
            walker.consume(node)
        }
        return walker.finish()
    }

    /**
     * Streaming node consumer. `cursor`/control-point state lives in the raw
     * path space (relative and reflected commands compose there); every point
     * appended to the output is mapped through [transform] first. Affine maps
     * of Bézier control points transform the curve exactly, so subdividing
     * post-transform matches iOS flattening a pre-transformed path.
     */
    private class NodeWalker(
        private val step: Double,
        private val transform: Affine,
    ) {
        private val polylines = mutableListOf<WobblePolyline>()
        private var current = mutableListOf<WobblePoint>()
        private var subpathStart = WobblePoint(0.0, 0.0)
        private var cursor = WobblePoint(0.0, 0.0)

        /** Previous cubic control2 / quad control, for S/T reflection. */
        private var lastCubicControl: WobblePoint? = null
        private var lastQuadControl: WobblePoint? = null

        fun consume(node: PathNode) {
            when (node) {
                is PathNode.MoveTo -> moveTo(WobblePoint(node.x.toDouble(), node.y.toDouble()))
                is PathNode.RelativeMoveTo -> moveTo(WobblePoint(cursor.x + node.dx, cursor.y + node.dy))
                is PathNode.LineTo -> lineTo(WobblePoint(node.x.toDouble(), node.y.toDouble()))
                is PathNode.RelativeLineTo -> lineTo(WobblePoint(cursor.x + node.dx, cursor.y + node.dy))
                is PathNode.HorizontalTo -> lineTo(WobblePoint(node.x.toDouble(), cursor.y))
                is PathNode.RelativeHorizontalTo -> lineTo(WobblePoint(cursor.x + node.dx, cursor.y))
                is PathNode.VerticalTo -> lineTo(WobblePoint(cursor.x, node.y.toDouble()))
                is PathNode.RelativeVerticalTo -> lineTo(WobblePoint(cursor.x, cursor.y + node.dy))
                is PathNode.QuadTo ->
                    quadTo(
                        WobblePoint(node.x1.toDouble(), node.y1.toDouble()),
                        WobblePoint(node.x2.toDouble(), node.y2.toDouble()),
                    )
                is PathNode.RelativeQuadTo ->
                    quadTo(
                        WobblePoint(cursor.x + node.dx1, cursor.y + node.dy1),
                        WobblePoint(cursor.x + node.dx2, cursor.y + node.dy2),
                    )
                is PathNode.ReflectiveQuadTo -> quadTo(reflectedQuadControl(), WobblePoint(node.x.toDouble(), node.y.toDouble()))
                is PathNode.RelativeReflectiveQuadTo ->
                    quadTo(reflectedQuadControl(), WobblePoint(cursor.x + node.dx, cursor.y + node.dy))
                is PathNode.CurveTo ->
                    cubicTo(
                        WobblePoint(node.x1.toDouble(), node.y1.toDouble()),
                        WobblePoint(node.x2.toDouble(), node.y2.toDouble()),
                        WobblePoint(node.x3.toDouble(), node.y3.toDouble()),
                    )
                is PathNode.RelativeCurveTo ->
                    cubicTo(
                        WobblePoint(cursor.x + node.dx1, cursor.y + node.dy1),
                        WobblePoint(cursor.x + node.dx2, cursor.y + node.dy2),
                        WobblePoint(cursor.x + node.dx3, cursor.y + node.dy3),
                    )
                is PathNode.ReflectiveCurveTo ->
                    cubicTo(
                        reflectedCubicControl(),
                        WobblePoint(node.x1.toDouble(), node.y1.toDouble()),
                        WobblePoint(node.x2.toDouble(), node.y2.toDouble()),
                    )
                is PathNode.RelativeReflectiveCurveTo ->
                    cubicTo(
                        reflectedCubicControl(),
                        WobblePoint(cursor.x + node.dx1, cursor.y + node.dy1),
                        WobblePoint(cursor.x + node.dx2, cursor.y + node.dy2),
                    )
                is PathNode.ArcTo ->
                    arcTo(
                        node.horizontalEllipseRadius.toDouble(),
                        node.verticalEllipseRadius.toDouble(),
                        node.theta.toDouble(),
                        node.isMoreThanHalf,
                        node.isPositiveArc,
                        WobblePoint(node.arcStartX.toDouble(), node.arcStartY.toDouble()),
                    )
                is PathNode.RelativeArcTo ->
                    arcTo(
                        node.horizontalEllipseRadius.toDouble(),
                        node.verticalEllipseRadius.toDouble(),
                        node.theta.toDouble(),
                        node.isMoreThanHalf,
                        node.isPositiveArc,
                        WobblePoint(cursor.x + node.arcStartDx, cursor.y + node.arcStartDy),
                    )
                PathNode.Close -> close()
            }
        }

        fun finish(): List<WobblePolyline> {
            flush(closed = false)
            return polylines
        }

        // ── Command primitives (mirror the iOS applyWithBlock cases) ──

        private fun moveTo(point: WobblePoint) {
            flush(closed = false)
            cursor = point
            subpathStart = point
            current = mutableListOf(map(point))
            resetReflection()
        }

        private fun lineTo(end: WobblePoint) {
            appendLine(end)
            resetReflection()
        }

        private fun quadTo(
            control: WobblePoint,
            end: WobblePoint,
        ) {
            appendQuad(control, end)
            lastQuadControl = control
            lastCubicControl = null
        }

        private fun cubicTo(
            control1: WobblePoint,
            control2: WobblePoint,
            end: WobblePoint,
        ) {
            appendCubic(control1, control2, end)
            lastCubicControl = control2
            lastQuadControl = null
        }

        private fun close() {
            // Subdivide the implicit closing leg, then drop the duplicated
            // start point: rings are stored without repetition because the
            // outline builder wraps neighbors cyclically.
            appendLine(subpathStart)
            val start = map(subpathStart)
            val last = current.lastOrNull()
            if (last != null &&
                current.size > 1 &&
                abs(last.x - start.x) < DUPLICATE_EPSILON &&
                abs(last.y - start.y) < DUPLICATE_EPSILON
            ) {
                current.removeAt(current.lastIndex)
            }
            flush(closed = true)
            cursor = subpathStart
            resetReflection()
        }

        // ── Subdivision (identical chord math to iOS) ──────────────

        private fun appendLine(end: WobblePoint) {
            val a = map(cursor)
            val b = map(end)
            val distance = hypot(b.x - a.x, b.y - a.y)
            val count = max(1, ceil(distance / step).toInt())
            for (i in 1..count) {
                val t = i.toDouble() / count
                append(WobblePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
            cursor = end
        }

        private fun appendQuad(
            control: WobblePoint,
            end: WobblePoint,
        ) {
            // Control-polygon length over-estimates arc length, which only
            // makes chords denser than `step` — never sparser.
            val start = map(cursor)
            val c = map(control)
            val e = map(end)
            val approxLength = hypot(c.x - start.x, c.y - start.y) + hypot(e.x - c.x, e.y - c.y)
            val count = max(1, ceil(approxLength / step).toInt())
            for (i in 1..count) {
                val t = i.toDouble() / count
                val mt = 1 - t
                append(
                    WobblePoint(
                        mt * mt * start.x + 2 * mt * t * c.x + t * t * e.x,
                        mt * mt * start.y + 2 * mt * t * c.y + t * t * e.y,
                    ),
                )
            }
            cursor = end
        }

        private fun appendCubic(
            control1: WobblePoint,
            control2: WobblePoint,
            end: WobblePoint,
        ) {
            val start = map(cursor)
            val c1 = map(control1)
            val c2 = map(control2)
            val e = map(end)
            val approxLength =
                hypot(c1.x - start.x, c1.y - start.y) +
                    hypot(c2.x - c1.x, c2.y - c1.y) +
                    hypot(e.x - c2.x, e.y - c2.y)
            val count = max(1, ceil(approxLength / step).toInt())
            for (i in 1..count) {
                val t = i.toDouble() / count
                val mt = 1 - t
                val c0 = mt * mt * mt
                val k1 = 3 * mt * mt * t
                val k2 = 3 * mt * t * t
                val k3 = t * t * t
                append(
                    WobblePoint(
                        c0 * start.x + k1 * c1.x + k2 * c2.x + k3 * e.x,
                        c0 * start.y + k1 * c1.y + k2 * c2.y + k3 * e.y,
                    ),
                )
            }
            cursor = end
        }

        /**
         * SVG arc → cubic segments (W3C endpoint-to-center parameterization,
         * spec appendix F.6), each ≤ 90° so the standard tangent-length
         * approximation holds; the cubics then flow through [appendCubic].
         * iOS gets this conversion from its SVG path parser before flattening,
         * so cross-platform chords differ slightly here — acceptable per PR
         * #556 ("vertex-exact overlay is a non-goal"); the parity gates are
         * the noise/displacement fixtures.
         */
        private fun arcTo(
            radiusX: Double,
            radiusY: Double,
            xAxisRotationDegrees: Double,
            largeArc: Boolean,
            sweep: Boolean,
            end: WobblePoint,
        ) {
            if (cursor == end) return
            if (radiusX == 0.0 || radiusY == 0.0) {
                lineTo(end)
                return
            }
            var rx = abs(radiusX)
            var ry = abs(radiusY)
            val phi = Math.toRadians(xAxisRotationDegrees)
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            // (F.6.5.1) midpoint-relative start in the rotated frame.
            val dx2 = (cursor.x - end.x) / 2
            val dy2 = (cursor.y - end.y) / 2
            val x1p = cosPhi * dx2 + sinPhi * dy2
            val y1p = -sinPhi * dx2 + cosPhi * dy2

            // (F.6.6) scale radii up if the endpoints can't fit them.
            val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
            if (lambda > 1) {
                val s = sqrt(lambda)
                rx *= s
                ry *= s
            }

            // (F.6.5.2) center in the rotated frame.
            val rxSq = rx * rx
            val rySq = ry * ry
            val numerator = rxSq * rySq - rxSq * y1p * y1p - rySq * x1p * x1p
            val denominator = rxSq * y1p * y1p + rySq * x1p * x1p
            val radicand = max(0.0, numerator / denominator)
            val sign = if (largeArc != sweep) 1.0 else -1.0
            val coefficient = sign * sqrt(radicand)
            val cxp = coefficient * (rx * y1p / ry)
            val cyp = coefficient * (-ry * x1p / rx)

            // (F.6.5.3) center + angles in the original frame.
            val cx = cosPhi * cxp - sinPhi * cyp + (cursor.x + end.x) / 2
            val cy = sinPhi * cxp + cosPhi * cyp + (cursor.y + end.y) / 2
            val startAngle = angle((x1p - cxp) / rx, (y1p - cyp) / ry)
            var sweepAngle = angle((-x1p - cxp) / rx, (-y1p - cyp) / ry) - startAngle
            if (!sweep && sweepAngle > 0) sweepAngle -= 2 * Math.PI
            if (sweep && sweepAngle < 0) sweepAngle += 2 * Math.PI

            val segments = max(1, ceil(abs(sweepAngle) / (Math.PI / 2)).toInt())
            val delta = sweepAngle / segments
            // Tangent length for a cubic approximating a `delta` elliptical arc.
            val tangent = 4.0 / 3.0 * kotlin.math.tan(delta / 4)
            var theta = startAngle
            for (i in 0 until segments) {
                val next = theta + delta
                val from = ellipsePoint(cx, cy, rx, ry, cosPhi, sinPhi, theta)
                val to = ellipsePoint(cx, cy, rx, ry, cosPhi, sinPhi, next)
                val fromTangent = ellipseTangent(rx, ry, cosPhi, sinPhi, theta)
                val toTangent = ellipseTangent(rx, ry, cosPhi, sinPhi, next)
                cubicTo(
                    WobblePoint(from.x + tangent * fromTangent.x, from.y + tangent * fromTangent.y),
                    WobblePoint(to.x - tangent * toTangent.x, to.y - tangent * toTangent.y),
                    // Land the final segment exactly on the command's endpoint.
                    if (i == segments - 1) end else to,
                )
                theta = next
            }
            resetReflection()
        }

        // ── Helpers ────────────────────────────────────────────────

        private fun flush(closed: Boolean) {
            if (current.isNotEmpty()) {
                polylines.add(WobblePolyline(current.toList(), closed))
            }
            current = mutableListOf()
        }

        private fun append(point: WobblePoint) {
            val last = current.lastOrNull()
            if (last != null &&
                abs(last.x - point.x) < DUPLICATE_EPSILON &&
                abs(last.y - point.y) < DUPLICATE_EPSILON
            ) {
                return
            }
            current.add(point)
        }

        private fun map(point: WobblePoint): WobblePoint =
            if (transform === Affine.IDENTITY) {
                point
            } else {
                WobblePoint(
                    transform.a * point.x + transform.c * point.y + transform.e,
                    transform.b * point.x + transform.d * point.y + transform.f,
                )
            }

        private fun reflectedQuadControl(): WobblePoint {
            val last = lastQuadControl ?: return cursor
            return WobblePoint(2 * cursor.x - last.x, 2 * cursor.y - last.y)
        }

        private fun reflectedCubicControl(): WobblePoint {
            val last = lastCubicControl ?: return cursor
            return WobblePoint(2 * cursor.x - last.x, 2 * cursor.y - last.y)
        }

        private fun resetReflection() {
            lastCubicControl = null
            lastQuadControl = null
        }

        private fun angle(
            x: Double,
            y: Double,
        ): Double = kotlin.math.atan2(y, x)

        private fun ellipsePoint(
            cx: Double,
            cy: Double,
            rx: Double,
            ry: Double,
            cosPhi: Double,
            sinPhi: Double,
            theta: Double,
        ): WobblePoint {
            val x = rx * cos(theta)
            val y = ry * sin(theta)
            return WobblePoint(cx + cosPhi * x - sinPhi * y, cy + sinPhi * x + cosPhi * y)
        }

        /** Derivative of [ellipsePoint] w.r.t. theta (unscaled direction). */
        private fun ellipseTangent(
            rx: Double,
            ry: Double,
            cosPhi: Double,
            sinPhi: Double,
            theta: Double,
        ): WobblePoint {
            val dx = -rx * sin(theta)
            val dy = ry * cos(theta)
            return WobblePoint(cosPhi * dx - sinPhi * dy, sinPhi * dx + cosPhi * dy)
        }
    }
}
