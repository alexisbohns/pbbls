import CoreGraphics

/// Stroke geometry for the pebble layer-tracing renderers.
///
/// The composed pebble SVG authors the outline (`layer:shape`) at
/// `stroke-width="6"` in viewBox units, but the glyph (`layer:glyph`) is
/// authored thinner (`6 × zoneScale`). Tracing every layer at the outline's
/// weight makes the glyph read the same weight as the outline — the fix for
/// issue #509.
enum PebbleStroke {

    /// The outline's authored stroke width in viewBox units. Mirrors the
    /// engine's shape/glyph stored stroke (`GLYPH_STROKE_WIDTH`, and the `6`
    /// on every shape path in the engine's shape templates).
    static let outlineWidth: CGFloat = 6

    /// On-screen `lineWidth` for a layer traced into `frame`, proportional to
    /// how the viewBox fits the frame (uniform min-axis scale, matching
    /// `LayerShape`'s fit). Keeps the stroke's absolute weight consistent with
    /// the outline at any thumbnail size.
    static func lineWidth(viewBox: CGRect, frame: CGSize) -> CGFloat {
        guard viewBox.width > 0, viewBox.height > 0 else { return outlineWidth }
        let scale = min(frame.width / viewBox.width, frame.height / viewBox.height)
        return outlineWidth * scale
    }
}
