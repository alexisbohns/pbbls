import SwiftUI

/// One Usual tile (souls spec S2): the drawing, the name, and when chosen a
/// tonal fill with a tick badge at the top right. Four across; `GlyphThumbnail`
/// keeps the 6 px stroke of the glyph space.
struct SoulTile: View {
    let soul: SoulWithGlyph
    let isSelected: Bool
    /// Browse mode: no selection, the tile opens the person.
    var showsSelection = true
    let onTap: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.xs + 2) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 44,
                               strokeColor: isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .overlay(alignment: .topTrailing) {
                        if isSelected && showsSelection { badge }
                    }
                Text(verbatim: soul.name)
                    .pebblesFont(isSelected ? .subheadEmphasized : .subhead)
                    .foregroundStyle(isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 88)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(isSelected && showsSelection ? Color.pebbles.primary.opacity(0.20) : Color.clear)
            )
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: soul.name))
        .accessibilityAddTraits(isSelected && showsSelection ? [.isSelected] : [])
    }

    private var badge: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Color.pebbles.onPrimary)
            .frame(width: 20, height: 20)
            .background(Circle().fill(Color.pebbles.primary))
            .offset(x: 8, y: -4)
            .accessibilityHidden(true)
    }
}

#Preview {
    let glyph = Glyph(id: SystemGlyph.default, name: nil,
                      strokes: [GlyphStroke(
                    d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)],
                      viewBox: "0 0 200 200", userId: nil)
    let soul = SoulWithGlyph(id: UUID(), name: "Maman", glyphId: glyph.id, glyph: glyph, pebblesCount: 12)
    return HStack {
        SoulTile(soul: soul, isSelected: true, onTap: {})
        SoulTile(soul: soul, isSelected: false, onTap: {})
    }
    .padding()
}
