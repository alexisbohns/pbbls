import CoreGraphics
import Foundation

/// Where each part of a lit stone sits: the web's `stone-geometry.ts`
/// transcribed (#1690), through the Android port. Three spaces meet in one stone:
///
/// - outline units: the backdrop silhouette's own viewBox (337 × 270 for
///   small). The body and its clip are drawn in it.
/// - canvas units: the engine canvas (250 × 200 for small) fitted and centred
///   into the outline box, through `canvasTransform`. The material constants
///   lifted from the web mock (facets, specks, sheen, glint) are read verbatim
///   inside it.
/// - seed units: the engine canvas again, but placed with the carving inset
///   (`pebbleScale × 1.22`), through `carveTransform`. Veins, fossil and glyph
///   live here.
///
/// Pixel constants are the mock's, measured on its phone stones; they convert
/// to outline units through `unitsPerPx`. The rings are not here:
/// `StoneRingGeometry` already covers them.
enum LitStoneGeometry {

    /// The carving's inset relative to the engine's (the retired Metal stone's `carvingScale`).
    static let carveScale: CGFloat = 1.22

    /// The glyph's carved line, whatever the stone's size.
    static let carveStrokePx: CGFloat = 1.25

    /// The two blurred copies under the stone, pushed away from the light.
    static let shadowFarPx = CGVector(dx: 3.86, dy: 6.95)
    static let shadowNearPx = CGVector(dx: 0.93, dy: 2.01)

    /// The glyph's lit lip, drawn under the ink and pushed down-right.
    static let lipPx = CGVector(dx: 0.32, dy: 0.54)

    /// The sprite's blurs in canvas units, as the mock declared them.
    static let blur: (near: CGFloat, far: CGFloat, ao: CGFloat, soft: CGFloat, glow: CGFloat) =
        (near: 5, far: 15, ao: 7, soft: 12, glow: 4)

    /// The web flattens the silhouette at this step before taking its frame;
    /// match it or the centroids drift (`StoneRingGeometry.flattenStep`).
    private static let frameFlattenStep = 2.0

    private static let defaultGlyphSide: CGFloat = 200

    /// Stone width on a phone; the height follows the silhouette's aspect ratio.
    static func phoneWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small:  return 34
        case .medium: return 44
        case .large:  return 54
        }
    }

    /// The engine canvas for a size (all three polarities share it).
    static func seedCanvas(_ size: ValenceSizeGroup) -> CGSize {
        switch size {
        case .small:  return CGSize(width: 250, height: 200)
        case .medium: return CGSize(width: 260, height: 260)
        case .large:  return CGSize(width: 260, height: 310)
        }
    }

    /// The backdrop's view box, in outline units.
    static func outlineBox(_ size: ValenceSizeGroup) -> CGSize {
        PebbleOutlineGeometry.outlineSize(for: size)
    }

    /// Outline units per on-screen px on a phone.
    static func unitsPerPx(_ size: ValenceSizeGroup) -> CGFloat {
        outlineBox(size).width / phoneWidth(size)
    }

    /// Canvas units → outline units: the mock's stone, fitted to the silhouette box.
    static func canvasScale(_ size: ValenceSizeGroup) -> CGFloat {
        let outline = outlineBox(size)
        let seed = seedCanvas(size)
        return min(outline.width / seed.width, outline.height / seed.height)
    }

    /// Every polarity shares the canvas; the polarity is kept for parity with the web signature.
    static func canvasTransform(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> CGAffineTransform {
        centred(size, scale: canvasScale(size))
    }

    /// Seed units → outline units, with the carving inset (`pebbleScale × 1.22`).
    static func carveTransform(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> CGAffineTransform {
        centred(size, scale: canvasScale(size) * CGFloat(PebbleOutlineGeometry.pebbleScale(for: size)) * carveScale)
    }

    /// The glyph in its engine zone, in seed units: `translate(x y) scale(zone / side)`.
    /// `side` is the glyph's own square side (`glyphSide(viewBox:)`).
    static func glyphTransform(_ size: ValenceSizeGroup, _ polarity: ValencePolarity, side: CGFloat) -> CGAffineTransform {
        StoneCarvingArt.glyphTransform(size: size, polarity: polarity, glyphViewBox: Double(side))
    }

    /// A glyph's square side from its `viewBox` ("0 0 200 200"); 200 if unreadable.
    static func glyphSide(viewBox: String) -> CGFloat {
        let parts = viewBox
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0 == "," || $0.isWhitespace })
        guard parts.count > 2, let side = Double(parts[2]), side.isFinite, side > 0 else { return defaultGlyphSide }
        return CGFloat(side)
    }

    /// The bounding box of the path's outer contour, in outline units; `.zero`
    /// for a path that does not parse.
    static func silhouetteFrame(_ d: String) -> CGRect {
        frame(d)?.box ?? .zero
    }

    /// The area centroid (shoelace) of the same outer contour; `.zero` for a
    /// path that does not parse or encloses no area. A `CGRect` cannot carry
    /// the web's `cx`/`cy`, so they live here.
    static func silhouetteCentroid(_ d: String) -> CGPoint {
        frame(d)?.centroid ?? .zero
    }

    private static func frame(_ d: String) -> (box: CGRect, centroid: CGPoint)? {
        guard let path = SVGPathParser.parse(d),
              // The first contour is the outer one; large-lowlight's second is its hole.
              let points = WobblePathFlattener.flatten(path, step: frameFlattenStep).first?.points,
              !points.isEmpty else { return nil }
        var minX = CGFloat.infinity
        var minY = CGFloat.infinity
        var maxX = -CGFloat.infinity
        var maxY = -CGFloat.infinity
        for p in points {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        let centroid = StoneRingGeometry.areaCentroid(points) ?? .zero
        return (CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY), centroid)
    }

    private static func centred(_ size: ValenceSizeGroup, scale: CGFloat) -> CGAffineTransform {
        let outline = outlineBox(size)
        let seed = seedCanvas(size)
        let tx = (outline.width - seed.width * scale) / 2
        let ty = (outline.height - seed.height * scale) / 2
        return CGAffineTransform(a: scale, b: 0, c: 0, d: scale, tx: tx, ty: ty)
    }
}
