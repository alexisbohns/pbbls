import CoreGraphics
import Foundation

/// The wobbled render of one artwork piece: the leaky filled ink, plus the
/// displaced centerline the appear animation's reveal mask strokes along.
final class WobbleArt {
    let ink: CGPath
    let centerline: CGPath

    init(ink: CGPath, centerline: CGPath) {
        self.ink = ink
        self.centerline = centerline
    }
}

/// Converts flattened centerline polylines into wobbled filled outlines,
/// porting the playground's baked-export algorithm: offset both stroke edges
/// from the centerline first, then displace every contour point independently.
/// The independent displacement is what makes the width breathe ("leaky") —
/// a stroked wobbled centerline would stay constant-width.
enum WobbleOutlineBuilder {

    /// End caps are semicircles of `capSegments` arcs (capSegments − 1
    /// interior points), matching the playground.
    private static let capSegments = 6
    /// A zero-length polyline (single-tap carve dot) becomes a circle with
    /// this many segments. The playground drops dots; we keep them visible,
    /// matching the current renderer's round-cap dot.
    private static let dotSegments = 12

    static func art(
        for polylines: [WobblePolyline],
        halfWidth: Double,
        params: WobbleParams,
        noise: SVGTurbulence
    ) -> WobbleArt {
        let ink = CGMutablePath()
        let centerline = CGMutablePath()
        for polyline in polylines {
            for contour in contours(for: polyline, halfWidth: halfWidth) {
                guard contour.count > 2 else { continue }
                appendClosedPolygon(contour.map { params.displace($0, using: noise) }, to: ink)
            }
            appendCenterline(for: polyline, params: params, noise: noise, to: centerline)
        }
        return WobbleArt(ink: ink.copy() ?? ink, centerline: centerline.copy() ?? centerline)
    }

    /// Un-displaced outline contours for one polyline. Internal so tests can
    /// assert point counts and winding without decoding a displaced path.
    static func contours(for polyline: WobblePolyline, halfWidth: Double) -> [[CGPoint]] {
        let points = polyline.points
        let count = points.count

        if count == 1 {
            let cx = Double(points[0].x)
            let cy = Double(points[0].y)
            let circle = (0..<dotSegments).map { i -> CGPoint in
                let angle = 2 * Double.pi * Double(i) / Double(dotSegments)
                return CGPoint(x: cx + halfWidth * cos(angle), y: cy + halfWidth * sin(angle))
            }
            return [circle]
        }

        let normals = Self.normals(for: points, closed: polyline.isClosed)
        let left = (0..<count).map { i in
            CGPoint(
                x: Double(points[i].x) + normals[i].x * halfWidth,
                y: Double(points[i].y) + normals[i].y * halfWidth
            )
        }
        let right = (0..<count).map { i in
            CGPoint(
                x: Double(points[i].x) - normals[i].x * halfWidth,
                y: Double(points[i].y) - normals[i].y * halfWidth
            )
        }

        if polyline.isClosed {
            // Outer ring + reversed inner ring: opposite windings make the
            // pair render as an annulus under nonzero fill.
            return [left, right.reversed()]
        }

        var contour = left
        appendCapArc(
            to: &contour,
            center: points[count - 1],
            from: left[count - 1],
            halfWidth: halfWidth,
            tangentX: Double(points[count - 1].x - points[count - 2].x),
            tangentY: Double(points[count - 1].y - points[count - 2].y)
        )
        contour.append(contentsOf: right.reversed())
        appendCapArc(
            to: &contour,
            center: points[0],
            from: right[0],
            halfWidth: halfWidth,
            tangentX: Double(points[0].x - points[1].x),
            tangentY: Double(points[0].y - points[1].y)
        )
        return [contour]
    }

    // MARK: - Pieces

    /// Per-point normals from neighbor tangents; endpoints use their single
    /// adjacent segment, closed rings wrap cyclically.
    private static func normals(for points: [CGPoint], closed: Bool) -> [SIMD2<Double>] {
        let count = points.count
        var result: [SIMD2<Double>] = []
        result.reserveCapacity(count)
        for i in 0..<count {
            let prev = closed ? points[(i - 1 + count) % count] : points[max(0, i - 1)]
            let next = closed ? points[(i + 1) % count] : points[min(count - 1, i + 1)]
            let tx = Double(next.x - prev.x)
            let ty = Double(next.y - prev.y)
            let rawLength = hypot(tx, ty)
            let length = rawLength == 0 ? 1 : rawLength   // playground: `|| 1`
            result.append(SIMD2(-ty / length, tx / length))
        }
        return result
    }

    /// Appends the interior points of a semicircular end cap around `center`,
    /// starting from `from` and bulging along the tangent direction.
    private static func appendCapArc(
        to contour: inout [CGPoint],
        center: CGPoint,
        from: CGPoint,
        halfWidth: Double,
        tangentX: Double,
        tangentY: Double
    ) {
        let cx = Double(center.x)
        let cy = Double(center.y)
        let startAngle = atan2(Double(from.y) - cy, Double(from.x) - cx)
        let midAngle = startAngle + .pi / 2
        let direction: Double = (cos(midAngle) * tangentX + sin(midAngle) * tangentY) >= 0 ? 1 : -1
        for step in 1..<capSegments {
            let angle = startAngle + direction * (.pi * Double(step) / Double(capSegments))
            contour.append(CGPoint(
                x: cx + halfWidth * cos(angle),
                y: cy + halfWidth * sin(angle)
            ))
        }
    }

    private static func appendClosedPolygon(_ points: [CGPoint], to path: CGMutablePath) {
        guard let first = points.first else { return }
        path.move(to: first)
        for point in points.dropFirst() {
            path.addLine(to: point)
        }
        path.closeSubpath()
    }

    /// The displaced centerline mirrors the input subpath structure so the
    /// reveal mask's `.trim` progresses across subpaths in the same order as
    /// today's stroke draw-on.
    private static func appendCenterline(
        for polyline: WobblePolyline,
        params: WobbleParams,
        noise: SVGTurbulence,
        to path: CGMutablePath
    ) {
        let displaced = polyline.points.map { params.displace($0, using: noise) }
        guard let first = displaced.first else { return }
        path.move(to: first)
        if displaced.count == 1 {
            // Zero-length segment: the mask stroke's round cap reveals the dot.
            path.addLine(to: first)
            return
        }
        for point in displaced.dropFirst() {
            path.addLine(to: point)
        }
        if polyline.isClosed {
            path.closeSubpath()
        }
    }
}
