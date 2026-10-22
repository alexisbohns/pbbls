import SwiftUI

/// A collection's mark on the held card's band (#1145 D9): its glyph when the
/// reference cache has one, else the generic stack mark (Android's
/// `ic_pebble_collection`). Decorative: the band's label names the collection.
struct CollectionMark: View {
    let glyph: Glyph?
    let ink: Color
    var side: CGFloat = 14

    var body: some View {
        Group {
            if let strokes = glyph?.strokes, !strokes.isEmpty {
                GlyphThumbnail(strokes: strokes, side: side, strokeColor: ink)
            } else {
                Image(systemName: "square.stack.3d.up")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(ink)
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }
}
