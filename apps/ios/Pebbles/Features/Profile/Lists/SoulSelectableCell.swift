import SwiftUI

/// Selection variant of `SoulGridCell`. Same 96pt glyph thumbnail + name
/// label, plus a 2pt accent ring and a checkmark badge in the top-right
/// when selected. Tap toggles via `onToggle`.
struct SoulSelectableCell: View {
    let soul: SoulWithGlyph
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    GlyphThumbnail(strokes: soul.glyph.strokes, side: 96)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(
                                    isSelected ? Color.pebblesAccent : Color.clear,
                                    lineWidth: 2
                                )
                        )

                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Color.pebblesAccent, Color.pebblesBackground)
                            .padding(6)
                            .accessibilityHidden(true)
                    }
                }
                Text(soul.name)
                    .font(.callout)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }
}

#Preview("not selected") {
    SoulSelectableCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Héloïse",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200",
                userId: nil
            )
        ),
        isSelected: false,
        onToggle: {}
    )
    .padding()
}

#Preview("selected") {
    SoulSelectableCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Ingrid",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200",
                userId: nil
            )
        ),
        isSelected: true,
        onToggle: {}
    )
    .padding()
}
