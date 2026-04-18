// swiftlint:disable identifier_name
import CoreGraphics

/// Ramer-Douglas-Peucker path simplification.
/// Direct port of `apps/web/lib/utils/simplify-path.ts` (ε = 1.5 in callers).
enum PathSimplification {
    static func simplify(points: [CGPoint], epsilon: Double) -> [CGPoint] {
        guard points.count > 2 else { return points }

        var maxDist = 0.0
        var maxIndex = 0
        let first = points[0]
        let last = points[points.count - 1]

        for i in 1..<(points.count - 1) {
            let dist = perpendicularDistance(points[i], lineStart: first, lineEnd: last)
            if dist > maxDist {
                maxDist = dist
                maxIndex = i
            }
        }

        if maxDist > epsilon {
            let left = simplify(points: Array(points[0...maxIndex]), epsilon: epsilon)
            let right = simplify(points: Array(points[maxIndex..<points.count]), epsilon: epsilon)
            return Array(left.dropLast()) + right
        }

        return [first, last]
    }

    private static func perpendicularDistance(
        _ point: CGPoint,
        lineStart: CGPoint,
        lineEnd: CGPoint
    ) -> Double {
        let dx = lineEnd.x - lineStart.x
        let dy = lineEnd.y - lineStart.y
        let lengthSq = Double(dx * dx + dy * dy)

        if lengthSq == 0 {
            let ex = Double(point.x - lineStart.x)
            let ey = Double(point.y - lineStart.y)
            return (ex * ex + ey * ey).squareRoot()
        }

        let t = max(
            0.0,
            min(
                1.0,
                Double((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSq
            )
        )
        let projX = Double(lineStart.x) + t * Double(dx)
        let projY = Double(lineStart.y) + t * Double(dy)
        let ex = Double(point.x) - projX
        let ey = Double(point.y) - projY
        return (ex * ex + ey * ey).squareRoot()
    }
}
// swiftlint:enable identifier_name
