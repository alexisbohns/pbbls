import SwiftUI

/// The gem's colours (#1226, D10): each facet in its family's four-stop ramp
/// at the facet's own tone, an uncoloured facet in the page's neutral pushed
/// toward the ground, mixed in OKLab as the board does. Android: `GemCanvas`'s
/// `facetFills` and `StoneTones.mixOklab`.
struct GemColors {
    /// Each allocated facet's flat colour, by id.
    let fills: [String: Color]
    /// The seams and the table's edge: the page's ground.
    let seam: Color
    /// The table's window of paper.
    let table: Color

    private static let rampAt: [Float] = [0, 0.3, 0.62, 1]

    init(facets: [String: String?], palette: (String) -> EmotionPalette?, environment: EnvironmentValues) {
        let night = environment.colorScheme == .dark
        let ground = Color.pebbles.surface.resolve(in: environment)
        let stock = Color.pebbles.stock.resolve(in: environment)
        let ink = Color.pebbles.onSurface.resolve(in: environment)
        // mix(a, b, t) in the board's sense: from a toward b by t.
        func mix(_ a: Color.Resolved, _ b: Color.Resolved, _ t: Float) -> Color.Resolved {
            OKLab.mix(b, t, a)
        }
        let neutral = night
            ? [mix(stock, ink, 0.30), mix(stock, ink, 0.19), mix(stock, ink, 0.11), mix(ground, ink, 0.05)]
            : [stock, mix(ground, ink, 0.05), mix(ground, ink, 0.12), mix(ground, ink, 0.22)]
        var stops: [String: [Color.Resolved]] = [:]
        func stopsOf(_ slug: String) -> [Color.Resolved]? {
            if let known = stops[slug] { return known }
            guard let p = palette(slug) else { return nil }
            let light = p.light.resolve(in: environment)
            let secondary = p.secondary.resolve(in: environment)
            let primary = p.primary.resolve(in: environment)
            let made = night
                ? [mix(light, secondary, 0.35), secondary, primary, mix(primary, ground, 0.42)]
                : [mix(light, secondary, 0.15), secondary, primary, p.shaded.resolve(in: environment)]
            stops[slug] = made
            return made
        }
        func ramp(_ stops: [Color.Resolved], _ tone: Float) -> Color.Resolved {
            let t = min(max(tone, 0), 1)
            for index in 1..<Self.rampAt.count where t <= Self.rampAt[index] {
                let from = Self.rampAt[index - 1]
                return mix(stops[index - 1], stops[index], (t - from) / (Self.rampAt[index] - from))
            }
            return stops[stops.count - 1]
        }
        var fills: [String: Color] = [:]
        for facet in GemGeometry.facets where facet.kind != .table {
            let tone = Float(facet.tone)
            let family = (facets[facet.id] ?? nil).flatMap(stopsOf)
            fills[facet.id] = Color(family.map { ramp($0, tone) } ?? mix(ramp(neutral, tone), ground, 0.45))
        }
        self.fills = fills
        seam = Color(ground)
        table = Color(night ? mix(stock, ink, 0.07) : stock)
    }
}

/// CSS `color-mix(in oklab, a weight%, b)`: linear sRGB → OKLab (Björn
/// Ottosson's matrices), interpolated, back, clamped, opaque. The endpoints
/// return the input itself. Android: `StoneTones.mixOklab`.
enum OKLab {
    static func mix(_ a: Color.Resolved, _ weight: Float, _ b: Color.Resolved) -> Color.Resolved {
        let w = Double(min(max(weight, 0), 1))
        if w == 1 { return a }
        if w == 0 { return b }
        let la = lab(a)
        let lb = lab(b)
        let mixed = (0..<3).map { lb[$0] + (la[$0] - lb[$0]) * w }
        return resolved(mixed)
    }

    private static func lab(_ color: Color.Resolved) -> [Double] {
        let r = Double(color.linearRed)
        let g = Double(color.linearGreen)
        let b = Double(color.linearBlue)
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return [0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s]
    }

    private static func resolved(_ lab: [Double]) -> Color.Resolved {
        func cube(_ v: Double) -> Double { v * v * v }
        let l = cube(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2])
        let m = cube(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2])
        let s = cube(lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2])
        func clamp(_ v: Double) -> Float { Float(min(max(v, 0), 1)) }
        return Color.Resolved(colorSpace: .sRGBLinear,
                              red: clamp(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                              green: clamp(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                              blue: clamp(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s))
    }
}
