import CoreGraphics
import Foundation
import SwiftUI

/// One glitter dot, grain speck or pit, in canvas units.
struct LitDot: Equatable {
    let cx: Double
    let cy: Double
    let r: Double
    let opacity: Double
    let slot: LitSlot
}

/// One facet of a gem. `edge` is the hairline from the face corner out along
/// the facet's edge; the face itself has none.
struct LitFacet: Equatable {
    let points: [CGPoint]
    let slot: LitSlot
    let opacity: Double
    let edge: (CGPoint, CGPoint)?

    static func == (lhs: LitFacet, rhs: LitFacet) -> Bool {
        guard lhs.points == rhs.points, lhs.slot == rhs.slot, lhs.opacity == rhs.opacity else { return false }
        switch (lhs.edge, rhs.edge) {
        case (nil, nil): return true
        case let (a?, b?): return a.0 == b.0 && a.1 == b.1
        default: return false
        }
    }
}

struct LitEllipse: Equatable {
    let cx: Double
    let cy: Double
    let rx: Double
    let ry: Double
    let rotate: Double
    let opacity: Double
}

/// The glint a press runs across the stone: fixed per size, in canvas units.
struct LitGlintGeometry: Equatable {
    /// The sweep band before it travels; it crosses `travel` units to the right.
    struct Sweep: Equatable {
        let x: Double
        let y: Double
        let width: Double
        let height: Double
        let rotate: Double
        let cx: Double
        let cy: Double
    }

    let sheen: LitEllipse
    let sweep: Sweep
    let travel: Double
    let spark: Path
}

/// The seeded detail of one lit stone: the web's `stone-recipe.ts` transcribed
/// (#1690), through the Android port, in canvas units
/// (`LitStoneGeometry.seedCanvas`), with positions drawn from the pebble's own
/// seed (`StableHash.seed`, the web's `hashUUID`) so the same pebble draws the
/// same stone on every surface and no two gems sparkle alike.
///
/// - gem (highlight): five facets around a pentagon plus its face, ten glitter
///   dots, two sparkles at the light;
/// - river pebble (neutral): fourteen grain specks, mostly lit, and a soft
///   diffuse light (matte, unlike the gem's shine);
/// - lava (lowlight): nine pits and a faint highlight.
///
/// Every drawn number goes through the web's `r2` (two decimals) so the
/// geometry is identical across surfaces, not merely close.
enum LitStoneRecipe: Equatable {
    case river(dots: [LitDot], light: LitEllipse)
    case lava(dots: [LitDot], light: LitEllipse)
    case gem(facets: [LitFacet], dots: [LitDot], sparkles: [Path])

    var dots: [LitDot] {
        switch self {
        case .river(let dots, _), .lava(let dots, _), .gem(_, let dots, _): return dots
        }
    }

    // MARK: Constants

    /// The mock's face, as fractions of the canvas; the outer facet corners are
    /// the face corners pushed five times their distance from its centre.
    private static let face: [(Double, Double)] = [
        (0.31, 0.21), (0.59, 0.16), (0.75, 0.37), (0.53, 0.53), (0.27, 0.42),
    ]

    private static let facetShade: [(slot: LitSlot, opacity: Double)] = [
        (.white, 0.2), (.white, 0.07), (.dark, 0.16), (.dark, 0.26), (.white, 0.1),
    ]

    /// How far a face corner may wander, as a fraction of the canvas.
    private static let faceJitter = 0.025
    private static let faceOpacity = 0.14

    private struct DotRule {
        let count: Int
        /// Radius and opacity ranges; radius in px on the phone stone.
        let r: (Double, Double)
        let opacity: (Double, Double)
        /// The box the dots fall in, as fractions of the canvas.
        let x: (Double, Double)
        let y: (Double, Double)
        let slot: (_ rand: () -> Double) -> LitSlot
    }

    private static func rule(_ polarity: ValencePolarity) -> DotRule {
        switch polarity {
        case .highlight:
            return DotRule(count: 10, r: (0.36, 0.87), opacity: (0.4, 0.94),
                           x: (0.17, 0.84), y: (0.18, 0.82), slot: { _ in .light })
        case .neutral:
            return DotRule(count: 14, r: (0.27, 0.58), opacity: (0.25, 0.54),
                           x: (0.12, 0.88), y: (0.19, 0.87),
                           slot: { rand in rand() < 0.7 ? .light : .shaded })
        case .lowlight:
            return DotRule(count: 9, r: (0.4, 0.99), opacity: (0.27, 0.6),
                           x: (0.18, 0.82), y: (0.19, 0.78), slot: { _ in .dark })
        }
    }

    // MARK: Random

    /// mulberry32 on 32-bit words: a small, fast, well-mixed PRNG, bit for bit
    /// the web's `random` (`Math.imul` is the wrapping multiply, `>>>` the
    /// unsigned shift).
    static func random(seed: UInt32) -> () -> Double {
        var a = seed
        return {
            a = a &+ 0x6D2B_79F5
            var t = a
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ (t ^ (t >> 7)) &* (t | 61)
            return Double(t ^ (t >> 14)) / 4_294_967_296
        }
    }

    private static func between(_ rand: () -> Double, _ range: (Double, Double)) -> Double {
        range.0 + rand() * (range.1 - range.0)
    }

    /// The web's `r2`: two decimals, halves up as JS `Math.round`.
    private static func r2(_ n: Double) -> Double {
        (n * 100 + 0.5).rounded(.down) / 100
    }

    // MARK: Star

    /// A number the way JS prints an `r2` result: no trailing zeros, no bare point.
    private static func fmt(_ n: Double) -> String {
        var s = String(format: "%.2f", r2(n))
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s == "-0" ? "0" : s
    }

    /// The web's `starPath` data byte for byte: a four-point star, its arms
    /// bowed in to `0.16` of the radius.
    static func starPathData(cx: Double, cy: Double, r: Double) -> String {
        let k = r * 0.16
        func p(_ x: Double, _ y: Double) -> String { "\(fmt(x)) \(fmt(y))" }
        return "M\(p(cx, cy - r))Q\(p(cx + k, cy - k)) \(p(cx + r, cy))Q\(p(cx + k, cy + k)) \(p(cx, cy + r))"
            + "Q\(p(cx - k, cy + k)) \(p(cx - r, cy))Q\(p(cx - k, cy - k)) \(p(cx, cy - r))Z"
    }

    static func starPath(cx: Double, cy: Double, r: Double) -> Path {
        guard let cg = SVGPathParser.parse(starPathData(cx: cx, cy: cy, r: r)) else { return Path() }
        return Path(cg)
    }

    // MARK: Recipe

    static func of(_ size: ValenceSizeGroup, _ polarity: ValencePolarity, seed: UInt32) -> LitStoneRecipe {
        let canvas = LitStoneGeometry.seedCanvas(size)
        let w = Double(canvas.width)
        let h = Double(canvas.height)
        let unitsPerPx = w / Double(LitStoneGeometry.phoneWidth(size))
        let rand = random(seed: seed)

        let rule = rule(polarity)
        var dots: [LitDot] = []
        for _ in 0..<rule.count {
            // Draw order is the web's: x, y, r, opacity, then the slot.
            let cx = r2(between(rand, rule.x) * w)
            let cy = r2(between(rand, rule.y) * h)
            let r = r2(between(rand, rule.r) * unitsPerPx)
            let opacity = r2(between(rand, rule.opacity))
            dots.append(LitDot(cx: cx, cy: cy, r: r, opacity: opacity, slot: rule.slot(rand)))
        }

        switch polarity {
        case .neutral:
            // Matte: a broad, dim wash on the lit side rather than a specular spot.
            return .river(dots: dots, light: LitEllipse(
                cx: 0.38 * w, cy: 0.3 * h, rx: 0.34 * w, ry: 0.2 * h, rotate: -16, opacity: 0.16))
        case .lowlight:
            return .lava(dots: dots, light: LitEllipse(
                cx: 0.34 * w, cy: 0.24 * h, rx: 0.26 * w, ry: 0.15 * h, rotate: 0, opacity: 0.07))
        case .highlight:
            return gem(dots: dots, rand: rand, w: w, h: h, unitsPerPx: unitsPerPx)
        }
    }

    private static func gem(dots: [LitDot], rand: () -> Double, w: Double, h: Double,
                            unitsPerPx: Double) -> LitStoneRecipe {
        var corners: [(Double, Double)] = []
        for (x, y) in face {
            // x is drawn before y, as the web's array literal evaluates them.
            let cx = r2((x + (rand() * 2 - 1) * faceJitter) * w)
            let cy = r2((y + (rand() * 2 - 1) * faceJitter) * h)
            corners.append((cx, cy))
        }
        let count = Double(corners.count)
        let cx = corners.reduce(0) { $0 + $1.0 } / count
        let cy = corners.reduce(0) { $0 + $1.1 } / count
        let outer = corners.map { (r2(6 * $0.0 - 5 * cx), r2(6 * $0.1 - 5 * cy)) }
        let faceP = corners.map { CGPoint(x: $0.0, y: $0.1) }
        let outerP = outer.map { CGPoint(x: $0.0, y: $0.1) }

        var facets: [LitFacet] = faceP.indices.map { i in
            let next = (i + 1) % faceP.count
            return LitFacet(points: [faceP[i], faceP[next], outerP[next], outerP[i]],
                            slot: facetShade[i].slot, opacity: facetShade[i].opacity,
                            edge: (faceP[i], outerP[i]))
        }
        facets.append(LitFacet(points: faceP, slot: .white, opacity: faceOpacity, edge: nil))

        // The two sparkles sit where the light lands; their size is the mock's, in px.
        let sparkles = [
            starPath(cx: 0.33 * w, cy: 0.23 * h, r: 2.65 * unitsPerPx),
            starPath(cx: 0.63 * w, cy: 0.3 * h, r: 1.55 * unitsPerPx),
        ]
        return .gem(facets: facets, dots: dots, sparkles: sparkles)
    }

    static func glint(_ size: ValenceSizeGroup) -> LitGlintGeometry {
        let canvas = LitStoneGeometry.seedCanvas(size)
        let w = Double(canvas.width)
        let h = Double(canvas.height)
        return LitGlintGeometry(
            sheen: LitEllipse(cx: 0.38 * w, cy: 0.3 * h, rx: 0.3 * w, ry: 0.14 * h, rotate: -24, opacity: 0.9),
            sweep: LitGlintGeometry.Sweep(x: -0.55 * w, y: -0.4 * h, width: 0.38 * w, height: 1.8 * h,
                                          rotate: 22, cx: w / 2, cy: h / 2),
            travel: 1.6 * w,
            spark: starPath(cx: 0.33 * w, cy: 0.25 * h, r: 0.085 * w))
    }
}
