import SwiftUI

/// Square preview of a glyph. Renders each `GlyphStroke.d` via `SVGPath.path(from:)`
/// inside a 200x200 coordinate space, scaled to the requested side length.
///
/// Used in:
/// - `PebbleFormView` glyph row (32pt)
/// - `GlyphPickerSheet` grid cells (~100pt)
/// - `GlyphsListView` grid cells (~100pt)
/// - `GlyphCarveSheet` "saved" confirmation (200pt)
struct GlyphThumbnail: View {
    let strokes: [GlyphStroke]
    var side: CGFloat = 100
    var strokeColor: Color = .primary
    var backgroundColor: Color = Color.secondary.opacity(0.08)

    var body: some View {
        Canvas { ctx, size in
            // Scale 200x200 glyph coords to the requested frame size.
            let scale = size.width / 200.0
            for stroke in strokes {
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
        .background(backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

#Preview {
    GlyphThumbnail(
        strokes: [
            GlyphStroke(d: "M30,30 L170,170", width: 6),
            GlyphStroke(d: "M170,30 L30,170", width: 6)
        ],
        side: 120
    )
    .padding()
}
