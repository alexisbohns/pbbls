import SwiftUI

/// A soul's mark (#1130 R7): its glyph in ink-2, or a dashed square when the
/// reference cache has no glyph for that soul (the web's `SoulMark`).
struct SoulMark: View {
    let strokes: [GlyphStroke]?
    let side: CGFloat

    var body: some View {
        Group {
            if let strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: side, strokeColor: Color.pebbles.onSurfaceVariant)
            } else {
                RoundedRectangle(cornerRadius: side * 0.2)
                    .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    @MainActor private static var images: [String: Image] = [:]

    /// The mark as an `Image` for a `Text` concatenation, rendered once per
    /// glyph, side, scheme and display scale.
    @MainActor
    static func image(strokes: [GlyphStroke]?, side: CGFloat, scheme: ColorScheme, scale: CGFloat) -> Image {
        var hasher = Hasher()
        hasher.combine(strokes)
        let key = "\(hasher.finalize())|\(side)|\(scheme)|\(scale)"
        if let cached = images[key] { return cached }
        let renderer = ImageRenderer(content: SoulMark(strokes: strokes, side: side).environment(\.colorScheme, scheme))
        renderer.scale = scale
        let image = renderer.uiImage.map { Image(uiImage: $0) } ?? Image(systemName: "square.dashed")
        images[key] = image
        return image
    }
}
