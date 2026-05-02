import SwiftUI

/// Selection variant of `SoulGridCell`. Square 96pt frame with a rounded
/// border (no fill) and the glyph drawn directly inside — no inner
/// background. When unselected the glyph is rendered in
/// `pebblesForeground` and the border is a faint `pebblesAccent` tint;
/// when selected the glyph and border switch to full `pebblesAccent`,
/// a `checkmark.circle.fill` badge appears in the top-right, and the
/// name label below becomes medium-weight `pebblesAccent`.
struct SoulSelectableCell: View {
    let soul: SoulWithGlyph
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    GlyphThumbnail(
                        strokes: soul.glyph.strokes,
                        side: 96,
                        strokeColor: isSelected ? Color.pebblesAccent : Color.pebblesMutedForeground,
                        backgroundColor: .clear
                    )
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isSelected ? Color.pebblesAccent : Color.pebblesAccent.opacity(0.15),
                                lineWidth: isSelected ? 2 : 1
                            )
                    }

                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Color.pebblesAccent)
                            .padding(6)
                            .accessibilityHidden(true)
                    }
                }
                Text(soul.name)
                    .font(.callout)
                    .fontWeight(isSelected ? .medium : .regular)
                    .foregroundStyle(isSelected ? Color.pebblesAccent : Color.pebblesMutedForeground)
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
            name: "Edgar",
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
            name: "Globule",
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
