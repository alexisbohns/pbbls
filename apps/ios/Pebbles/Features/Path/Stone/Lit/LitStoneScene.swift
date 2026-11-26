import SwiftUI
import os

/// One lit stone's resolved inputs, in points: Android's `StonePainter.Scene`
/// (#1690). Built once per paint; the silhouette and the carvings behind it are
/// memoised by their own owners.
///
/// Three scales meet here. `pxPerUnit` takes outline units to points. `unitPx`
/// is the size of one phone px at this stone's width: the shadows and the glyph's
/// lip are offsets in those, so they grow with the stone. `canvasScale` and
/// `carveScale` are the canvas and the carving's own units in points, for
/// radii, blurs and the veins' widths.
struct LitStoneScene {
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let palette: StonePalette
    let blank: Bool
    let glyph: PebbleGlyph?
    let seed: UInt32
    let pxPerUnit: CGFloat
    let unitPx: CGFloat
    let width: CGFloat
    let height: CGFloat
    let body: Path
    let evenOdd: Bool
    /// Canvas units → points.
    let canvasMatrix: CGAffineTransform
    let canvasScale: CGFloat
    let carveScale: CGFloat
    /// The stone's width over its phone width, so a stone drawn larger carves a heavier line.
    let carveGain: CGFloat
    let recipe: LitStoneRecipe
    private let carveTransform: CGAffineTransform

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone")

    /// Nil only when the silhouette asset is missing (already logged by its loader).
    init?(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) {
        let blank = spec.palette == nil
        // The web draws a blank moment as a neutral river pebble whatever its polarity.
        let polarity = blank ? ValencePolarity.neutral : spec.polarity
        // Only the lowlight silhouette is wobbled (D7); the gem and the river pebble keep the soft outline.
        let art = polarity == .lowlight
            ? WobbleRenderer.backdropArt(size: spec.size, polarity: polarity)
            : WobbleRenderer.softBackdropArt(size: spec.size, polarity: polarity)
        guard let art else {
            Self.logger.error("lit stone: no silhouette for \(spec.size.rawValue, privacy: .public)-\(polarity.rawValue, privacy: .public)")
            return nil
        }
        let outline = LitStoneGeometry.outlineBox(spec.size)
        let pxPerUnit = box.width / outline.width
        let canvas = LitStoneGeometry.canvasTransform(spec.size, polarity)
        let scaled = CGAffineTransform(scaleX: pxPerUnit, y: pxPerUnit)

        self.size = spec.size
        self.polarity = polarity
        self.palette = spec.palette ?? LitStoneTones.blankPalette
        self.blank = blank
        self.glyph = spec.glyph
        self.seed = spec.seed
        self.pxPerUnit = pxPerUnit
        self.unitPx = LitStoneGeometry.unitsPerPx(spec.size) * pxPerUnit
        self.width = outline.width * pxPerUnit
        self.height = outline.height * pxPerUnit
        var scaledBody = scaled
        self.body = Path(art.path.copy(using: &scaledBody) ?? art.path)
        self.evenOdd = art.usesEvenOddFill
        self.canvasMatrix = canvas.concatenating(scaled)
        self.canvasScale = canvas.a * pxPerUnit
        let carve = LitStoneGeometry.carveTransform(spec.size, polarity)
        self.carveTransform = carve
        self.carveScale = carve.a * pxPerUnit
        // Android rounds the phone width to whole device px, so a default stone's gain is exactly 1.
        let defaultWidth = (LitStoneGeometry.phoneWidth(spec.size) * scale).rounded()
        self.carveGain = defaultWidth > 0 ? box.width * scale / defaultWidth : 1
        self.recipe = LitStoneRecipe.of(spec.size, polarity, seed: spec.seed)
    }

    var fillStyle: FillStyle { FillStyle(eoFill: evenOdd) }

    /// Seed units → points, with a seed-unit offset (a lip, a groove's far wall) applied first.
    func carveMatrix(_ offset: CGVector = .zero) -> CGAffineTransform {
        CGAffineTransform(translationX: offset.dx, y: offset.dy)
            .concatenating(carveTransform)
            .concatenating(CGAffineTransform(scaleX: pxPerUnit, y: pxPerUnit))
    }

    /// A glyph's own space → points: its engine zone, then the carving's inset.
    func glyphMatrix(side: CGFloat) -> CGAffineTransform {
        LitStoneGeometry.glyphTransform(size, polarity, side: side)
            .concatenating(carveMatrix())
    }
}
