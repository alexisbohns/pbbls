import CoreGraphics
import Foundation

/// The logo split into reveal groups, in the SVG's own viewBox space, before
/// wobbling. Each stroke is kept as its OWN `CGPath` (one per SVG `<path>`) so
/// the draw-on can mask every stroke by its own centerline — a combined path
/// would let one stroke's fat reveal mask expose a neighbour's ink (#598).
struct LogoParsedGroups {
    let viewBox: CGRect
    /// The enclosing stone outline (reveal phase 1) — one stroke.
    let outline: [CGPath]
    /// The creature strokes except the eyes (reveal phase 2).
    let creatureStrokes: [CGPath]
    /// Fossil strokes + the two pebble veins (reveal phase 3).
    let fossilAndVeins: [CGPath]
    /// The two eyes — filled regions, no trim reveal (revealed together).
    let eyeFills: CGPath
}

extension CGPath {
    /// True when the path encloses no area (empty combined group).
    var isEmptyGroup: Bool { boundingBoxOfPath.isNull || boundingBoxOfPath.isEmpty }
}

enum LogoLoaderArt {

    private static let resourceName = "pbbls-logo-loader"

    /// Parse the bundled loader SVG into reveal groups. Returns nil if the
    /// asset is missing or unparseable.
    static func parseGroups() -> LogoParsedGroups? {
        guard
            let url = Bundle.main.url(forResource: resourceName, withExtension: "svg"),
            let raw = try? String(contentsOf: url, encoding: .utf8),
            let viewBox = parseViewBox(in: raw)
        else { return nil }

        var outline: [CGPath] = []
        var creature: [CGPath] = []
        var fossilVeins: [CGPath] = []
        let eyes = CGMutablePath()

        for element in pathElements(in: raw) {
            guard let path = SVGPathParser.parse(element.d) else { continue }
            switch bucket(for: element) {
            case .outline:      outline.append(path)
            case .creature:     creature.append(path)
            case .fossilVeins:  fossilVeins.append(path)
            case .eyes:         eyes.addPath(path)
            }
        }

        return LogoParsedGroups(
            viewBox: viewBox,
            outline: outline,
            creatureStrokes: creature,
            fossilAndVeins: fossilVeins,
            eyeFills: eyes.copy() ?? eyes
        )
    }

    // MARK: - Bucketing

    private enum Bucket { case outline, creature, fossilVeins, eyes }

    private static func bucket(for element: PathElement) -> Bucket {
        let id = element.id
        if id == "pebble-outline" { return .outline }
        if id.hasPrefix("pebble-vein") || id.hasPrefix("fossil") { return .fossilVeins }
        if id.contains("eye") { return .eyes }
        // Remaining creature-* strokes.
        return .creature
    }

    // MARK: - Minimal SVG scanning

    // swiftlint:disable:next identifier_name
    struct PathElement { let id: String; let d: String }

    /// Every `<path>` element's `id` and `d`, in document order.
    private static func pathElements(in svg: String) -> [PathElement] {
        guard let regex = try? NSRegularExpression(pattern: "<path\\b[^>]*>") else { return [] }
        let full = NSRange(svg.startIndex..., in: svg)
        return regex.matches(in: svg, range: full).compactMap { match -> PathElement? in
            guard let range = Range(match.range, in: svg) else { return nil }
            let tag = String(svg[range])
            // swiftlint:disable:next identifier_name
            guard let d = attribute("d", in: tag) else { return nil }
            return PathElement(id: attribute("id", in: tag) ?? "", d: d)
        }
    }

    /// First `name="…"` value in `tag`. The word boundary keeps `d=` from
    /// matching `id=` (mirrors `WobbleRenderer.attribute`).
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

/// Wobbled artwork for the whole logo at one boil seed. Stroke groups hold one
/// `WobbleArt` per SVG path so each is masked independently during the draw-on.
struct LogoLoaderVariant {
    let outline: [WobbleArt]      // reveal phase 1
    let creature: [WobbleArt]     // reveal phase 2
    let fossilVeins: [WobbleArt]  // reveal phase 3
    let eyes: CGPath              // displaced fill regions (no reveal)
}

/// The parsed logo plus its three boil variants. Built once, cached.
struct LogoLoaderModel {
    let viewBox: CGRect
    let variants: [LogoLoaderVariant]
}

extension LogoLoaderArt {

    /// SVG-authored stroke half-width, in the logo's own viewBox units.
    private static let strokeHalfWidth = Double(PebbleStroke.outlineWidth) / 2   // 3
    /// Boil variant seeds (issue #555 §2.3: variant k → seed 3 + k).
    private static let seeds = [3, 4, 5]

    // Logo-specific wobble tuning (issue #598 polish). The loader logo has
    // finer features than a pebble glyph, so the canonical amplitude (#555:
    // 18 in the 200-box) reads as jagged here. A gentler amplitude smooths the
    // rendition WITHOUT touching pebble/glyph wobble, which keeps using
    // `WobbleParams.canonical`. Authored in the normalized 200-box; mapped into
    // the logo's own viewBox by `logoParams(for:)`. Tunable in the simulator.
    private static let logoAmplitude: Double = 9
    private static let logoFrequency: Double = 0.02
    private static let logoOctaves: Int = 5
    private static let logoFlattenStep: Double = 2

    /// The logo tuning mapped into the SVG's own viewBox — same §2.1 rule as
    /// `WobbleParams.scaled(for:)`, but off the logo-specific base above rather
    /// than the shared canonical params.
    private static func logoParams(for spaceSize: CGSize) -> WobbleParams {
        let base = WobbleParams(
            amplitude: logoAmplitude, frequency: logoFrequency,
            octaves: logoOctaves, flattenStep: logoFlattenStep
        )
        let longest = Double(max(spaceSize.width, spaceSize.height))
        guard longest > 0 else { return base }
        let normalization = 200 / longest
        return WobbleParams(
            amplitude: base.amplitude / normalization,
            frequency: base.frequency * normalization,
            octaves: base.octaves,
            flattenStep: base.flattenStep / normalization
        )
    }

    private static let cached: LogoLoaderModel? = buildUncached()

    /// Cached accessor — the wobble build runs at most once per process.
    static func build() -> LogoLoaderModel? { cached }

    private static func buildUncached() -> LogoLoaderModel? {
        guard let groups = parseGroups() else { return nil }
        // Wobble in the SVG's own viewBox with logo-tuned params — the
        // established backdrop pattern (WobbleRenderer.backdropArt), gentler
        // than the canonical glyph wobble so fine features read cleanly.
        let params = logoParams(for: groups.viewBox.size)
        let variants = seeds.map { seed -> LogoLoaderVariant in
            let noise = SVGTurbulence(seed: seed)
            return LogoLoaderVariant(
                outline: groups.outline.map { strokeArt($0, params: params, noise: noise) },
                creature: groups.creatureStrokes.map { strokeArt($0, params: params, noise: noise) },
                fossilVeins: groups.fossilAndVeins.map { strokeArt($0, params: params, noise: noise) },
                eyes: displacedFill(groups.eyeFills, params: params, noise: noise)
            )
        }
        return LogoLoaderModel(viewBox: groups.viewBox, variants: variants)
    }

    private static func strokeArt(_ path: CGPath, params: WobbleParams, noise: SVGTurbulence) -> WobbleArt {
        let polylines = WobblePathFlattener.flatten(path, step: params.flattenStep)
        return WobbleOutlineBuilder.art(for: polylines, halfWidth: strokeHalfWidth, params: params, noise: noise)
    }

    /// Displace a fill region's contours directly (mirrors
    /// `WobbleRenderer.backdropArt` — fills wobble their edge, no outline).
    private static func displacedFill(_ path: CGPath, params: WobbleParams, noise: SVGTurbulence) -> CGPath {
        let out = CGMutablePath()
        for polyline in WobblePathFlattener.flatten(path, step: params.flattenStep) {
            let displaced = polyline.points.map { params.displace($0, using: noise) }
            guard displaced.count > 2, let first = displaced.first else { continue }
            out.move(to: first)
            for point in displaced.dropFirst() { out.addLine(to: point) }
            out.closeSubpath()
        }
        return out.copy() ?? out
    }
}
