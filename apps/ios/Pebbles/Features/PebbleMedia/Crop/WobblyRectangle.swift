import SwiftUI

/// A rectangle drawn by hand (spec "hand-drawn parts"): each corner nudged and
/// each edge bowed in or out by up to `amount` of its length, the same way for
/// the same seed. Edges are curves, not lines, so it reads as a pebble, not a
/// sketch. With a `cornerRadius` each corner is eased off by a curve that
/// stays tangent to its two edges, so the wobble survives the rounding.
struct WobblyRectangle: Shape {
    let seed: String
    var amount: CGFloat = 0.03
    var cornerRadius: CGFloat = 0

    /// A cubic's control distance that approximates a circular quarter arc.
    private static let arcHandle: CGFloat = 0.5523

    func path(in rect: CGRect) -> Path {
        var rng = SeededGenerator(seed: seed)
        let nudge = min(rect.width, rect.height) * amount * 0.5
        let corners = [
            CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
            CGPoint(x: rect.maxX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY)
        ].map { CGPoint(x: $0.x + rng.unit() * nudge, y: $0.y + rng.unit() * nudge) }

        // Never more than half the shorter side, so opposite corners never meet.
        let radius = max(0, min(cornerRadius, min(rect.width, rect.height) / 2))
        // Each edge runs between the points `radius` in from its two corners
        // (the corners themselves without rounding).
        let starts = (0..<4).map { inset(corners[$0], toward: corners[($0 + 1) % 4], by: radius) }
        let ends = (0..<4).map { inset(corners[($0 + 1) % 4], toward: corners[$0], by: radius) }
        // Each edge's bowed control, drawn in edge order so the seed's sequence is
        // unchanged. A rounded edge keeps the bow's slope (not its depth), so the
        // short run between two corners stays nearly flat instead of kinking.
        let controls: [CGPoint?] = (0..<4).map { i in
            let from = corners[i], to = corners[(i + 1) % 4]
            let length = hypot(to.x - from.x, to.y - from.y)
            guard length > 0 else { return nil }
            // Outward normal for a clockwise outline in y-down space.
            let normal = CGPoint(x: (to.y - from.y) / length, y: -(to.x - from.x) / length)
            let bow = rng.unit() * length * amount
            let run = hypot(ends[i].x - starts[i].x, ends[i].y - starts[i].y)
            let depth = bow * (run / length)
            let mid = lerp(starts[i], ends[i], 0.5)
            // A quadratic's control at twice the bow puts the curve's midpoint at the bow.
            return CGPoint(x: mid.x + normal.x * depth * 2, y: mid.y + normal.y * depth * 2)
        }

        var path = Path()
        if radius == 0 {
            path.move(to: corners[0])
            for i in 0..<4 {
                let to = corners[(i + 1) % 4]
                if let control = controls[i] {
                    path.addQuadCurve(to: to, control: control)
                } else {
                    path.addLine(to: to)
                }
            }
            path.closeSubpath()
            return path
        }
        // Each corner is a cubic whose handles continue the tangents of the two
        // edges meeting there, so the join has no kink.
        let handle = radius * Self.arcHandle
        path.move(to: starts[0])
        for i in 0..<4 {
            let j = (i + 1) % 4
            let control = controls[i] ?? lerp(starts[i], ends[i], 0.5)
            let nextControl = controls[j] ?? lerp(starts[j], ends[j], 0.5)
            path.addQuadCurve(to: ends[i], control: control)
            path.addCurve(to: starts[j],
                          control1: advance(ends[i], from: control, by: handle),
                          control2: advance(starts[j], from: nextControl, by: handle))
        }
        path.closeSubpath()
        return path
    }

    /// `point` moved `distance` further along the direction from `origin` to it.
    private func advance(_ point: CGPoint, from origin: CGPoint, by distance: CGFloat) -> CGPoint {
        let length = hypot(point.x - origin.x, point.y - origin.y)
        guard length > 0 else { return point }
        return CGPoint(x: point.x + (point.x - origin.x) / length * distance,
                       y: point.y + (point.y - origin.y) / length * distance)
    }

    private func inset(_ point: CGPoint, toward other: CGPoint, by distance: CGFloat) -> CGPoint {
        let length = hypot(other.x - point.x, other.y - point.y)
        guard distance > 0, length > 0 else { return point }
        return lerp(point, other, min(distance / length, 0.5))
    }

    private func lerp(_ a: CGPoint, _ b: CGPoint, _ t: CGFloat) -> CGPoint {
        CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
    }
}
