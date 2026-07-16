package app.pbbls.android.features.glyph.carve

import kotlin.math.sqrt

/**
 * A point in carve space — Double math end to end, mirroring the iOS
 * `CGPoint`-based pipeline so simplification decisions can't drift.
 */
data class CarvePoint(
    val x: Double,
    val y: Double,
)

/**
 * Ramer–Douglas–Peucker simplification — ports iOS `PathSimplification.swift`
 * byte-for-byte in behavior (M43 design D1):
 *
 * - distance is to the **clamped segment** (t ∈ [0,1]), not the infinite line;
 * - the split is strict (`maxDist > epsilon`);
 * - the max-distance point lands in BOTH recursion halves, joined
 *   `left.dropLast(1) + right`;
 * - `size <= 2` (including empty) passes through unchanged.
 *
 * Epsilon is applied in canvas space (280dp side) BEFORE the ×(200/side)
 * viewBox scale — simplifying after scaling changes the output.
 */
object PathSimplification {
    fun simplify(
        points: List<CarvePoint>,
        epsilon: Double,
    ): List<CarvePoint> {
        if (points.size <= 2) return points
        val first = points.first()
        val last = points.last()
        var maxDist = 0.0
        var maxIndex = 0
        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }
        return if (maxDist > epsilon) {
            val left = simplify(points.subList(0, maxIndex + 1), epsilon)
            val right = simplify(points.subList(maxIndex, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /** Distance from [point] to the segment [lineStart]→[lineEnd], t clamped to [0,1]. */
    private fun perpendicularDistance(
        point: CarvePoint,
        lineStart: CarvePoint,
        lineEnd: CarvePoint,
    ): Double {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0.0) {
            return euclidean(point, lineStart)
        }
        val t = (((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        val projected = CarvePoint(lineStart.x + t * dx, lineStart.y + t * dy)
        return euclidean(point, projected)
    }

    private fun euclidean(
        a: CarvePoint,
        b: CarvePoint,
    ): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
