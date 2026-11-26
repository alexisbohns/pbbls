import Foundation

/// Warms the pieces of a lit stone that are safe to build off the main actor, so
/// the first stone the valence step paints does not pay for them (#1690). The
/// painter itself stays out: `ImageRenderer` is main-actor, and `LitStoneCache`
/// paints there on first request.
///
/// Every piece is a `static let` or sits behind a lock, so this is safe from any
/// thread and cheap to call twice.
enum LitStonePrewarm {
    static func run(scale: CGFloat) {
        // The nine carvings: parsed once into a fixed table.
        _ = LitStoneCarvings.carving(.small, .neutral)
        // The grain tile at the display scale.
        _ = LitStoneGrain.tile(scale: scale)
        // The sample glyph's wobbled lines, at the side the painter asks for.
        if let glyph = LitStoneSampleGlyph.glyph {
            let side = Double(LitStoneGeometry.glyphSide(viewBox: glyph.viewBox))
            for stroke in glyph.strokes { _ = LitGlyphLine.line(d: stroke.d, side: side) }
        }
    }
}
