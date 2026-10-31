import SwiftUI

/// You's glyph frame (#1382 D6, D17): a pentagon on its point, each corner a
/// cubic over 28 % of the side with handles at 4 %, each side bowed 2 pt out.
/// Ported from the c3 mock's `pentagon()`: R 64 about (64, 66) in a 128 × 122
/// design box, scaled to the rect.
struct SquirclePentagon: Shape {
    func path(in rect: CGRect) -> Path {
        let box = CGSize(width: 128, height: 122)
        let centre = CGPoint(x: 64, y: 66), radius: CGFloat = 64, t: CGFloat = 0.28, k: CGFloat = 0.04, bow: CGFloat = 2
        let sx = rect.width / box.width, sy = rect.height / box.height
        func place(_ p: CGPoint) -> CGPoint { CGPoint(x: rect.minX + p.x * sx, y: rect.minY + p.y * sy) }
        func lerp(_ a: CGPoint, _ b: CGPoint, _ u: CGFloat) -> CGPoint {
            CGPoint(x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u)
        }
        let corners = (0..<5).map { i -> CGPoint in
            let angle = -CGFloat.pi / 2 + CGFloat(i) * 2 * .pi / 5
            return CGPoint(x: centre.x + radius * cos(angle), y: centre.y + radius * sin(angle))
        }
        var path = Path()
        for i in 0..<5 {
            let prev = corners[(i + 4) % 5], vertex = corners[i], next = corners[(i + 1) % 5]
            let a = lerp(vertex, prev, t), b = lerp(vertex, next, t)
            if i == 0 { path.move(to: place(a)) }
            path.addCurve(to: place(b), control1: place(lerp(vertex, prev, k)), control2: place(lerp(vertex, next, k)))
            let nextStart = lerp(next, vertex, t), mid = lerp(b, nextStart, 0.5)
            let out = CGPoint(x: mid.x - centre.x, y: mid.y - centre.y), length = hypot(out.x, out.y)
            let control = CGPoint(x: mid.x + out.x / length * bow, y: mid.y + out.y / length * bow)
            path.addQuadCurve(to: place(nextStart), control: place(control))
        }
        path.closeSubpath()
        return path
    }
}
