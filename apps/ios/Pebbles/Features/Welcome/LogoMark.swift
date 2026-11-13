import CoreGraphics
import Foundation

/// The Pebbles mark — one filled outline (fill-rule evenodd) — and its wobble.
///
/// The mark is a filled shape, not strokes, so the wobble displaces the
/// outline's points with smooth, low-frequency, seeded noise (the same
/// `SVGTurbulence` / `WobbleParams.displace` the pebbles use). The displacement
/// is a function of position only, so neighbouring contours move together and
/// the spiral never crosses itself. Variants are built once and cached.
enum LogoMark {

    private static let resourceName = "pbbls-mark"

    /// The mark in its own viewBox space: the exact outline (the static look)
    /// and the wobbled variants the splash ping-pongs across.
    struct Model {
        let viewBox: CGRect
        /// The unwobbled outline — what a settled, static logo draws.
        let outline: CGPath
        /// Wobbled copies of the outline, one per wobble seed.
        let variants: [CGPath]
    }

    /// Wobble variant seeds (issue #555 §2.3: variant k → seed 3 + k).
    static let seeds = [3, 4, 5]

    /// Wobble tuning, authored in the mark's own 1024 viewBox. Low frequency
    /// (wavelength ≈ 250 units) keeps the shape reading as hand-made rather than
    /// jagged; the amplitude is set so the largest displacement is about 1.2% of
    /// the mark's size. (`flattenStep` is unused: the outline is displaced as curves.) Tunable in the simulator.
    static let params = WobbleParams(amplitude: 30, frequency: 0.004, octaves: 2, flattenStep: 3)

    private static let cached: Model? = buildUncached()

    /// Cached accessor — the parse and the wobble run at most once per process.
    static func build() -> Model? { cached }

    /// Parses the bundled SVG: the outline and its viewBox. Nil when the asset
    /// is missing or unparseable.
    static func parse() -> (viewBox: CGRect, outline: CGPath)? {
        guard
            let url = Bundle.main.url(forResource: resourceName, withExtension: "svg"),
            let raw = try? String(contentsOf: url, encoding: .utf8)
        else { return nil }
        return parse(svg: raw)
    }

    static func parse(svg: String) -> (viewBox: CGRect, outline: CGPath)? {
        guard
            let viewBox = parseViewBox(in: svg),
            let tag = firstPathTag(in: svg),
            let d = attribute("d", in: tag),
            let outline = SVGPathParser.parse(d),
            !outline.boundingBoxOfPath.isNull, !outline.boundingBoxOfPath.isEmpty
        else { return nil }
        return (viewBox, outline)
    }

    /// The outline with every point (anchors and curve controls) moved by one
    /// seed's noise. The outline is already a chain of short quadratic curves
    /// (about ten units each), far shorter than the noise wavelength, so moving
    /// its points moves the curves smoothly: the result stays a chain of
    /// curves, never a faceted polygon.
    static func wobble(_ outline: CGPath, seed: Int, params: WobbleParams = LogoMark.params) -> CGPath {
        let noise = SVGTurbulence(seed: seed)
        let out = CGMutablePath()
        func moved(_ point: CGPoint) -> CGPoint { params.displace(point, using: noise) }
        outline.applyWithBlock { pointer in
            let element = pointer.pointee
            switch element.type {
            case .moveToPoint:
                out.move(to: moved(element.points[0]))
            case .addLineToPoint:
                out.addLine(to: moved(element.points[0]))
            case .addQuadCurveToPoint:
                out.addQuadCurve(to: moved(element.points[1]), control: moved(element.points[0]))
            case .addCurveToPoint:
                out.addCurve(
                    to: moved(element.points[2]),
                    control1: moved(element.points[0]),
                    control2: moved(element.points[1])
                )
            case .closeSubpath:
                out.closeSubpath()
            @unknown default:
                break
            }
        }
        return out.copy() ?? out
    }

    private static func buildUncached() -> Model? {
        guard let parsed = parse() else { return nil }
        let variants = seeds.map { wobble(parsed.outline, seed: $0) }
        return Model(viewBox: parsed.viewBox, outline: parsed.outline, variants: variants)
    }

    // MARK: - Minimal SVG scanning

    private static func firstPathTag(in svg: String) -> String? {
        guard
            let regex = try? NSRegularExpression(pattern: "<path\\b[^>]*>"),
            let match = regex.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
            let range = Range(match.range, in: svg)
        else { return nil }
        return String(svg[range])
    }

    /// First `name="…"` value in `tag`. The word boundary keeps `d=` from
    /// matching `id=`.
    private static func attribute(_ name: String, in tag: String) -> String? {
        guard
            let regex = try? NSRegularExpression(pattern: "(?<![\\w-])\(name)=\"([^\"]*)\""),
            let match = regex.firstMatch(in: tag, range: NSRange(tag.startIndex..., in: tag)),
            let range = Range(match.range(at: 1), in: tag)
        else { return nil }
        return String(tag[range])
    }

    private static func parseViewBox(in svg: String) -> CGRect? {
        guard let value = attribute("viewBox", in: svg) else { return nil }
        let parts = value.split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
        guard parts.count == 4, parts[2] > 0, parts[3] > 0 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }
}

/// When the splash may hand over: once, the first time the app is ready —
/// immediately if it already is. No minimum duration, no waiting for a frame.
struct LogoSettleGate {
    private(set) var hasFired = false

    /// True exactly once: on the first call with `ready == true`.
    mutating func shouldFire(ready: Bool) -> Bool {
        guard ready, !hasFired else { return false }
        hasFired = true
        return true
    }
}
