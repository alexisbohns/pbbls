import CoreGraphics
import Foundation
import SwiftUI
import os

/// Per-layer wobbled artwork for a composed pebble SVG, index-aligned with
/// `PebbleSVGModel.layers`.
final class WobblePebbleArt {
    let layers: [WobbleArt]

    init(layers: [WobbleArt]) {
        self.layers = layers
    }
}

/// Wobbled backdrop silhouette, in its asset's viewBox space.
final class WobbleBackdropArt {
    let path: CGPath
    let viewBox: CGRect
    /// `large-lowlight.svg` carves a hole with `fill-rule="evenodd"`; the
    /// other eight assets fill nonzero.
    let usesEvenOddFill: Bool

    init(path: CGPath, viewBox: CGRect, usesEvenOddFill: Bool) {
        self.path = path
        self.viewBox = viewBox
        self.usesEvenOddFill = usesEvenOddFill
    }
}

/// Entry point of the wobble experiment: derives per-surface parameters
/// (issue #555 §2.1 spaces and half-widths), runs flatten → outline →
/// displace, and caches results so the cost is paid once per artwork —
/// never per frame.
enum WobbleRenderer {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "wobble")

    /// One noise field for the whole app: the static look is seed 3 (§1).
    private static let noise = SVGTurbulence(seed: WobbleParams.seed)

    // Keys are content strings: collision-proof, and a few KB per key is
    // negligible next to the cached paths. NSCache additionally evicts
    // under memory pressure.
    private static let pebbleCache: NSCache<NSString, WobblePebbleArt> = {
        let cache = NSCache<NSString, WobblePebbleArt>()
        cache.countLimit = 128
        return cache
    }()
    private static let glyphCache: NSCache<NSString, WobbleArt> = {
        let cache = NSCache<NSString, WobbleArt>()
        cache.countLimit = 512
        return cache
    }()
    private static let backdropCache = NSCache<NSString, WobbleBackdropArt>()

    // MARK: - Pebble render (layer:shape / layer:fossil / layer:glyph)

    static func pebbleArt(svg: String, model: PebbleSVGModel) -> WobblePebbleArt {
        if let cached = pebbleCache.object(forKey: svg as NSString) {
            return cached
        }
        let canvasParams = WobbleParams.scaled(for: model.viewBox.size)
        let layers = model.layers.map { layer -> WobbleArt in
            let params: WobbleParams
            let halfWidth: Double
            if layer.kind == .glyph, layer.transform.a > 0.01 {
                // Glyph paths live in the engine's 200-box slot space; the
                // layer transform scales them onto the canvas. Wobble in the
                // raw box with canonical params, and pre-divide the half-width
                // so the ink lands at the outline's weight after the
                // transform (#509 uniform-weight rule).
                params = .canonical
                halfWidth = Double(PebbleStroke.outlineWidth) / Double(layer.transform.a) / 2
            } else {
                // Shape and fossil are authored in canvas space. A glyph layer
                // with a degenerate transform (never emitted by the engine)
                // also degrades to this treatment rather than dividing by ~0.
                params = canvasParams
                halfWidth = Double(PebbleStroke.outlineWidth) / 2
            }
            let polylines = WobblePathFlattener.flatten(layer.combinedPath, step: params.flattenStep)
            return WobbleOutlineBuilder.art(for: polylines, halfWidth: halfWidth, params: params, noise: noise)
        }
        let art = WobblePebbleArt(layers: layers)
        pebbleCache.setObject(art, forKey: svg as NSString)
        return art
    }

    // MARK: - Backdrop silhouettes (bundled assets)

    static func backdropArt(size: ValenceSizeGroup, polarity: ValencePolarity) -> WobbleBackdropArt? {
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        if let cached = backdropCache.object(forKey: name as NSString) {
            return cached
        }
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let raw = try? String(contentsOf: url, encoding: .utf8) else {
            logger.error("wobble backdrop: missing outline asset \(name, privacy: .public).svg")
            return nil
        }
        guard let art = backdropArt(fromAsset: raw) else {
            logger.error("wobble backdrop: could not parse outline asset \(name, privacy: .public).svg")
            return nil
        }
        backdropCache.setObject(art, forKey: name as NSString)
        return art
    }

    /// Parses one outline asset — a single filled `<path>`, the verified shape
    /// of all nine bundled files — and displaces its contours. No outline
    /// building: the silhouette is already a fill region, so wobbling its edge
    /// is the whole effect.
    static func backdropArt(fromAsset raw: String) -> WobbleBackdropArt? {
        guard
            let viewBoxAttribute = attribute("viewBox", in: raw),
            let viewBox = parseViewBox(viewBoxAttribute),
            viewBox.width > 0, viewBox.height > 0,
            let pathData = attribute("d", in: raw),
            let path = SVGPathParser.parse(pathData)
        else { return nil }

        let params = WobbleParams.scaled(for: viewBox.size)
        let wobbled = CGMutablePath()
        for polyline in WobblePathFlattener.flatten(path, step: params.flattenStep) {
            let displaced = polyline.points.map { params.displace($0, using: noise) }
            guard displaced.count > 2, let first = displaced.first else { continue }
            wobbled.move(to: first)
            for point in displaced.dropFirst() {
                wobbled.addLine(to: point)
            }
            // Silhouette contours are fills — close them whether or not the
            // asset spelled out the trailing `Z`.
            wobbled.closeSubpath()
        }
        guard !wobbled.isEmpty else { return nil }
        return WobbleBackdropArt(
            path: wobbled.copy() ?? wobbled,
            viewBox: viewBox,
            usesEvenOddFill: raw.contains("fill-rule=\"evenodd\"")
        )
    }

    // MARK: - Glyph strokes (thumbnails, carve preview, pills)

    /// Wobbled filled ink for one raw glyph stroke in the 200-box space.
    /// Returns nil when the `d` string doesn't parse (caller falls back to
    /// the plain stroke).
    // swiftlint:disable:next identifier_name
    static func glyphInk(d: String, width: Double) -> CGPath? {
        let key = "\(width)|\(d)" as NSString
        if let cached = glyphCache.object(forKey: key) {
            return cached.ink
        }
        let path = SVGPath.path(from: d).cgPath
        guard !path.isEmpty else { return nil }
        let params = WobbleParams.canonical
        let polylines = WobblePathFlattener.flatten(path, step: params.flattenStep)
        guard !polylines.isEmpty else { return nil }
        let art = WobbleOutlineBuilder.art(for: polylines, halfWidth: width / 2, params: params, noise: noise)
        glyphCache.setObject(art, forKey: key)
        return art.ink
    }

    // MARK: - Minimal asset scanning

    /// First `name="…"` attribute value in `svg`. The word boundary keeps
    /// `d=` from matching `id=`.
    private static func attribute(_ name: String, in svg: String) -> String? {
        guard
            let regex = try? NSRegularExpression(pattern: "(?<![\\w-])\(name)=\"([^\"]*)\""),
            let match = regex.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
            let range = Range(match.range(at: 1), in: svg)
        else { return nil }
        return String(svg[range])
    }

    private static func parseViewBox(_ value: String) -> CGRect? {
        let parts = value
            .split(whereSeparator: { $0 == " " || $0 == "," })
            .compactMap { Double($0) }
        guard parts.count == 4 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }
}
