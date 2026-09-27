import CoreGraphics
import Foundation
import os

/// The carving of one stone: the engine shape's outline and veins, the sample
/// glyph placed in the engine's slot, all inked as wobbled fills and merged;
/// plus the fossil as a displaced region. Built once per valence and cached,
/// the way `ValenceArt` does it.
enum StoneCarvingArt {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone")

    /// `@unchecked Sendable`: immutable `CGPath` copies, never mutated.
    final class Art: @unchecked Sendable {
        /// The engine canvas for this size.
        let viewBox: CGRect
        /// Veins + glyph, wobbled ink, nonzero fill.
        let ink: CGPath
        /// The seed's veins alone, so a real pebble's carving can add its own glyph.
        let veins: CGPath
        /// The engine outline alone, wobbled ink, so the page can leave it out.
        let outline: CGPath
        /// The fossil's displaced region, with its fill rule.
        let fossil: WobbleBackdropArt?

        init(viewBox: CGRect, ink: CGPath, veins: CGPath, outline: CGPath, fossil: WobbleBackdropArt?) {
            self.viewBox = viewBox
            self.ink = ink
            self.veins = veins
            self.outline = outline
            self.fossil = fossil
        }
    }

    private struct GlyphFile: Decodable {
        struct Stroke: Decodable {
            // swiftlint:disable:next identifier_name
            let d: String
            let width: Double
        }
        let viewBox: Double
        let strokes: [Stroke]
    }

    private static let lock = NSLock()
    private static var cache: [Valence: Art] = [:]
    /// Carvings for real pebbles, keyed by their composed SVG. An `NSCache`
    /// so a long Path does not pin every pebble's paths forever.
    private static let pebbleCache = NSCache<NSString, Art>()

    static func art(for valence: Valence) -> Art? {
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache[valence] { return cached }
        guard let built = build(valence) else { return nil }
        cache[valence] = built
        return built
    }

    /// The carving of a real pebble: the seed's veins and fossil for its
    /// valence, plus the glyph (and any extra fossil layer) taken from the
    /// pebble's own composed SVG, wobbled by the same renderer the Path uses.
    /// The engine outline in that SVG is left out, as the seed's is. Nil when
    /// the seed is missing; a pebble whose SVG will not parse keeps the seed
    /// carving with no glyph, logged.
    static func art(for valence: Valence, pebbleSvg svg: String) -> Art? {
        let key = "\(valence.rawValue)|\(svg)" as NSString
        if let cached = pebbleCache.object(forKey: key) { return cached }
        guard let seed = art(for: valence) else { return nil }
        let ink = CGMutablePath()
        ink.addPath(seed.veins)
        if let model = PebbleSVGModel(svg: svg) {
            let wobbled = WobbleRenderer.pebbleArt(svg: svg, model: model)
            for (index, layer) in model.layers.enumerated() where layer.kind != .shape {
                var transform = layer.transform
                let layerInk = wobbled.layers[index].ink
                ink.addPath(layerInk.copy(using: &transform) ?? layerInk)
            }
        } else {
            logger.error("stone: pebble svg did not parse; carving without its glyph")
        }
        let built = Art(viewBox: seed.viewBox, ink: ink.copy() ?? ink, veins: seed.veins,
                        outline: seed.outline, fossil: seed.fossil)
        pebbleCache.setObject(built, forKey: key)
        return built
    }

    static func prewarm() {
        for valence in Valence.allCases { _ = art(for: valence) }
    }

    // MARK: - Engine layout (mirrors `_shared/engine/layout.ts`)

    private static let glyphSize: [ValenceSizeGroup: Double] = [.small: 140, .medium: 150, .large: 160]

    private static let glyphPosition: [ValenceSizeGroup: [ValencePolarity: CGPoint]] = [
        .small:  [.highlight: CGPoint(x: 37.5, y: 30), .neutral: CGPoint(x: 25, y: 30), .lowlight: CGPoint(x: 30, y: 30)],
        .medium: [.highlight: CGPoint(x: 26, y: 84),   .neutral: CGPoint(x: 26, y: 55), .lowlight: CGPoint(x: 39, y: 26)],
        .large:  [.highlight: CGPoint(x: 50, y: 75),   .neutral: CGPoint(x: 26, y: 88), .lowlight: CGPoint(x: 26, y: 75)],
    ]

    /// `translate(x, y) scale(s)` in CG row-vector order (scale first).
    static func glyphTransform(size: ValenceSizeGroup, polarity: ValencePolarity, glyphViewBox: Double) -> CGAffineTransform {
        let slot = glyphSize[size] ?? 150
        let origin = glyphPosition[size]?[polarity] ?? .zero
        let scale = slot / glyphViewBox
        return CGAffineTransform(scaleX: scale, y: scale)
            .concatenating(CGAffineTransform(translationX: origin.x, y: origin.y))
    }

    // MARK: - Build

    private static func build(_ valence: Valence) -> Art? {
        let name = "stone-shape-\(valence.sizeGroup.rawValue)-\(valence.polarity.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8),
              let parsed = StoneShapeParser.parse(svg) else {
            logger.error("stone: missing or unparseable seed \(name, privacy: .public)")
            return nil
        }

        let outline = CGMutablePath()
        if let path = WobbleRenderer.glyphInk(d: parsed.outline.d, width: parsed.outline.width) {
            outline.addPath(path)
        } else {
            logger.error("stone: outline of \(name, privacy: .public) did not wobble")
        }

        let veins = CGMutablePath()
        for (index, stroke) in parsed.veins.enumerated() {
            guard let path = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width) else {
                logger.error("stone: vein \(index, privacy: .public) of \(name, privacy: .public) did not wobble")
                continue
            }
            veins.addPath(path)
        }
        let ink = CGMutablePath()
        ink.addPath(veins)

        if let glyph = placedGlyphInk(size: valence.sizeGroup, polarity: valence.polarity) {
            ink.addPath(glyph)
        }

        var fossil: WobbleBackdropArt?
        if let fill = parsed.fossil {
            let asset = """
            <svg viewBox="\(parsed.viewBox.minX) \(parsed.viewBox.minY) \(parsed.viewBox.width) \(parsed.viewBox.height)">\
            <path d="\(fill.d)"\(fill.usesEvenOddFill ? " fill-rule=\"evenodd\"" : "")/></svg>
            """
            fossil = WobbleRenderer.backdropArt(fromAsset: asset)
            if fossil == nil {
                logger.error("stone: fossil of \(name, privacy: .public) did not wobble")
            }
        }

        guard !ink.isEmpty || !outline.isEmpty else {
            logger.error("stone: no ink for \(name, privacy: .public)")
            return nil
        }
        return Art(viewBox: parsed.viewBox, ink: ink.copy() ?? ink, veins: veins.copy() ?? veins,
                   outline: outline.copy() ?? outline, fossil: fossil)
    }

    /// The sample glyph's strokes, wobbled and placed in the engine's slot
    /// for this size and polarity, merged into one ink path. Nil when the
    /// glyph file is missing or no stroke wobbled.
    static func placedGlyphInk(size: ValenceSizeGroup, polarity: ValencePolarity) -> CGPath? {
        guard let glyph else { return nil }
        var transform = glyphTransform(size: size, polarity: polarity, glyphViewBox: glyph.viewBox)
        let placed = CGMutablePath()
        for (index, stroke) in glyph.strokes.enumerated() {
            guard let path = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width),
                  let moved = path.copy(using: &transform) else {
                logger.error("stone: glyph stroke \(index, privacy: .public) did not wobble")
                continue
            }
            placed.addPath(moved)
        }
        return placed.isEmpty ? nil : placed.copy()
    }

    /// Decoded once: a `static let` initialiser runs exactly once, thread-safely.
    private static let glyph: GlyphFile? = {
        guard let url = Bundle.main.url(forResource: "stone-glyph", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(GlyphFile.self, from: data) else {
            logger.error("stone: missing sample glyph")
            return nil
        }
        return file
    }()
}
