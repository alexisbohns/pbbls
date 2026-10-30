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
                // 14pt by default: the one place an icon draws under the 16 floor
                // (iOS migration spec D6), a 1.0pt stroke accepted on the band. A
                // bare template image, not `PbblsIconImage`: the slot is fixed, like
                // the glyph thumbnail beside it, and must not scale with Dynamic Type.
                Image(PbblsIcon.collection.assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(ink)
            }
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }
}
