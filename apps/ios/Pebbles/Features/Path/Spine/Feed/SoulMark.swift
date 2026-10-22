import SwiftUI
import UIKit
import os

/// A soul's mark (#1130 R7): its glyph in ink-2, or a dashed square when the
/// reference cache has no glyph for that soul (the web's `SoulMark`).
struct SoulMark: View {
    let strokes: [GlyphStroke]?
    let side: CGFloat
    var glyphInk: Color = Color.pebbles.onSurfaceVariant
    var placeholderInk: Color = Color.pebbles.outline

    /// The two inks, resolved by the caller for its scheme and contrast, so a mark
    /// drawn to an image off-screen matches the text around it.
    struct Inks: Hashable {
        let glyph: Color.Resolved
        let placeholder: Color.Resolved
    }

    var body: some View {
        Group {
            if let strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: side, strokeColor: glyphInk)
            } else {
                RoundedRectangle(cornerRadius: side * 0.2)
                    .strokeBorder(placeholderInk, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "spine")
    @MainActor private static var images: [String: Image] = [:]

    /// The mark as a decorative `Image` for a `Text` concatenation (VoiceOver
    /// reads the names, never the marks), rendered once per glyph, side, inks
    /// and display scale.
    @MainActor
    static func image(strokes: [GlyphStroke]?, side: CGFloat, inks: Inks, scale: CGFloat) -> Image {
        var hasher = Hasher()
        hasher.combine(strokes)
        hasher.combine(inks)
        let key = "\(hasher.finalize())|\(side)|\(scale)"
        if let cached = images[key] { return cached }
        let mark = SoulMark(strokes: strokes, side: side, glyphInk: Color(inks.glyph),
                            placeholderInk: Color(inks.placeholder))
        let renderer = ImageRenderer(content: mark)
        renderer.scale = scale
        let image: Image
        if let cgImage = renderer.cgImage {
            image = Image(decorative: cgImage, scale: scale)
        } else {
            logger.warning("soul mark did not render at side \(side, privacy: .public); leaving its room blank")
            image = blank(side: side, scale: scale)
        }
        images[key] = image
        return image
    }

    /// An empty, decorative square that keeps the mark's room in the line.
    private static func blank(side: CGFloat, scale: CGFloat) -> Image {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let empty = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in }
        return empty.cgImage.map { Image(decorative: $0, scale: scale) } ?? Image(decorative: "")
    }
}
