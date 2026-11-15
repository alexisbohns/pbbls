import SwiftUI

/// A rectangle drawn by hand (spec "hand-drawn parts"): each corner nudged and
/// each edge bowed in or out by up to `amount` of its length, the same way for
/// the same seed. Small corner rounding keeps it a pebble, not a sketch.
struct WobblyRectangle: Shape {
    let seed: String
    var amount: CGFloat = 0.03

    func path(in rect: CGRect) -> Path {
        var rng = SeededGenerator(seed: seed)
        let nudge = min(rect.width, rect.height) * amount * 0.5
        let corners = [
            CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
            CGPoint(x: rect.maxX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY)
        ].map { CGPoint(x: $0.x + rng.unit() * nudge, y: $0.y + rng.unit() * nudge) }

        var path = Path()
        path.move(to: corners[0])
        for i in 0..<4 {
            let from = corners[i], to = corners[(i + 1) % 4]
            let length = hypot(to.x - from.x, to.y - from.y)
            // Outward normal for a clockwise outline in y-down space.
            let normal = CGPoint(x: (to.y - from.y) / length, y: -(to.x - from.x) / length)
            let bow = rng.unit() * length * amount
            let mid = CGPoint(x: (from.x + to.x) / 2, y: (from.y + to.y) / 2)
            // A quadratic's control at twice the bow puts the curve's midpoint at the bow.
            let control = CGPoint(x: mid.x + normal.x * bow * 2, y: mid.y + normal.y * bow * 2)
            path.addQuadCurve(to: to, control: control)
        }
        path.closeSubpath()
        return path
    }
}
