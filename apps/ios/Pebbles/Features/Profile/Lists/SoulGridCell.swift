import SwiftUI

/// One cell in the Souls 3-column grid. Square glyph thumbnail above a
/// single-line truncating name. Tap target wraps the entire cell — tap
/// behaviour is owned by the parent `NavigationLink`, this view is purely
/// visual.
struct SoulGridCell: View {
    let soul: SoulWithGlyph

    var body: some View {
        VStack(spacing: 8) {
            GlyphThumbnail(strokes: soul.glyph.strokes, side: 96)
                .accessibilityHidden(true)
            Text(soul.name)
                .font(.callout)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
    }
}

#Preview {
    SoulGridCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Preview Soul",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200",
                userId: nil
            )
        )
    )
    .padding()
}
