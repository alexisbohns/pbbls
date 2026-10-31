import SwiftUI

/// The emotions rosace's facets (#1306, the board's `gem`): in a `side` square,
/// one kite per seat between the outer, middle and inner rings and one star
/// between two kites, seat 2.5 at twelve. Angles are radians from +x with y
/// down. Android: `RosaceGeometry`.
struct RosaceGeometry {
    let side: CGFloat
    let count: Int
    /// Labelled leaves room for the families' names around it.
    let labelled: Bool

    var centre: CGFloat { side / 2 }
    var outer: CGFloat { side * (labelled ? 0.345 : 0.44) }
    var mid: CGFloat { outer * 0.85 }
    var inner: CGFloat { outer * 0.47 }
    var step: Double { 2 * .pi / Double(max(count, 1)) }

    /// Seat `k`'s axis.
    func angle(_ k: Double) -> Double {
        -.pi / 2 + (k - 2.5) * step
    }

    func point(_ radius: CGFloat, _ angle: Double) -> CGPoint {
        CGPoint(x: centre + CGFloat(cos(angle)) * radius, y: centre + CGFloat(sin(angle)) * radius)
    }

    /// Seat `k`'s kite: left shoulder, tip, right shoulder, heel.
    func kite(_ k: Int) -> [CGPoint] {
        let a = angle(Double(k))
        return [point(mid, a - step / 2), point(outer, a), point(mid, a + step / 2), point(inner, a)]
    }

    /// The star between seats `k` and `k + 1`: heel, shared shoulder, next heel.
    func star(_ k: Int) -> [CGPoint] {
        [point(inner, angle(Double(k))), point(mid, angle(Double(k)) + step / 2), point(inner, angle(Double(k + 1)))]
    }

    /// The outline round every kite.
    var silhouette: [CGPoint] {
        (0..<count).flatMap { k in [point(mid, angle(Double(k)) - step / 2), point(outer, angle(Double(k)))] }
    }

    /// A family's boundary tick, half a step before its first seat, from 3 to 9 outside.
    func tick(start: Int) -> (CGPoint, CGPoint) {
        let a = angle(Double(start)) - step / 2
        return (point(outer + 3, a), point(outer + 9, a))
    }

    /// A family's name: 22 outside, at its middle seat.
    func label(start: Int, length: Int) -> CGPoint {
        point(outer + 22, angle(Double(start) + Double(length - 1) / 2))
    }

    /// A new emotion's dot, 6.5 outside its seat.
    func fresh(_ k: Int) -> CGPoint {
        point(outer + 6.5, angle(Double(k)))
    }

    /// A seat's place in its family's ramp, from 0 to 1 (the board's `t`).
    static func tone(index: Int, of length: Int) -> Double {
        length > 1 ? Double(index) / Double(length - 1) : 0.5
    }
}

/// The rosace's facet colours (the board's `tone`), mixed in OKLab.
enum RosaceColors {
    /// Paper: the family's secondary moved toward its shaded ink by 8 % plus 72 % of
    /// the seat's place. Night: toward its primary by 62 % of it.
    static func tone(_ palette: EmotionPalette, t: Double, scheme: ColorScheme,
                     environment: EnvironmentValues) -> Color.Resolved {
        let secondary = palette.secondary.resolve(in: environment)
        if scheme == .dark {
            return OKLab.mix(palette.primary.resolve(in: environment), Float((t * 62).rounded() / 100), secondary)
        }
        return OKLab.mix(palette.shaded.resolve(in: environment), Float((8 + t * 72).rounded() / 100), secondary)
    }

    /// The star between two met seats of one family: the next seat's tone, 38 % toward the stock.
    static func soft(_ tone: Color.Resolved, stock: Color.Resolved) -> Color.Resolved {
        OKLab.mix(stock, 0.38, tone)
    }
}
