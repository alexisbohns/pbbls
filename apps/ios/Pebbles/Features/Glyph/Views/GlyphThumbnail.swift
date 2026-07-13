import SwiftUI

/// Pure stroke canvas for a glyph. Renders each `GlyphStroke.d` via
/// `SVGPath.path(from:)` inside a 200x200 coordinate space, scaled to the
/// requested side length. No background, no clipping — chrome is the
/// caller's responsibility (see `GlyphView` for the canonical wrapper).
///
/// Direct callers after the #459 refactor:
/// - `SoulPill` (path)         — explicitly chrome-less inside a pill
/// - `PebbleMetaPill` (path)   — explicitly chrome-less inside a pill
/// - `GlyphView` (composition) — owns 34-radius border + state colors
struct GlyphThumbnail: View {
    let strokes: [GlyphStroke]
    var side: CGFloat = 100
    var strokeColor: Color = .primary

    var body: some View {
        Canvas { ctx, size in
            let scale = size.width / 200.0
            for stroke in strokes {
                if WobbleFlags.isEnabled,
                   let ink = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width) {
                    // Wobble experiment (#555): thickness is baked into the
                    // filled outline; scale it like the stroked path below.
                    ctx.fill(
                        Path(ink).applying(CGAffineTransform(scaleX: scale, y: scale)),
                        with: .color(strokeColor)
                    )
                    continue
                }
                var path = SVGPath.path(from: stroke.d)
                path = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                ctx.stroke(
                    path,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: stroke.width * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
        }
        .frame(width: side, height: side)
    }
}

#Preview {
    GlyphThumbnail(
        strokes: [
            GlyphStroke(d: "M30,30 L170,170", width: 6),
            GlyphStroke(d: "M170,30 L30,170", width: 6)
        ],
        side: 120,
        strokeColor: .primary
    )
    .padding()
}
