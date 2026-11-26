import CoreGraphics
import Foundation
import os

/// The engine's carving tables and its bundled shape seeds. The vector stone
/// reads them through `LitStoneGeometry` and `LitStoneCarvings`; the web
/// glyph-zone drift test (`stone-geometry.test.ts`) reads the two tables below
/// from this file, so their text shape stays as it is.
enum StoneCarvingArt {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone")

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

    // MARK: - Seeds

    /// One bundled engine shape seed, split into outline, veins and fossil.
    /// Nil (and logged) when the seed is missing or does not parse.
    static func parsedSeed(size: ValenceSizeGroup, polarity: ValencePolarity) -> StoneShapeParser.Parsed? {
        let name = seedName(size: size, polarity: polarity)
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8),
              let parsed = StoneShapeParser.parse(svg) else {
            logger.error("stone: missing or unparseable seed \(name, privacy: .public)")
            return nil
        }
        return parsed
    }

    private static func seedName(size: ValenceSizeGroup, polarity: ValencePolarity) -> String {
        "stone-shape-\(size.rawValue)-\(polarity.rawValue)"
    }
}
