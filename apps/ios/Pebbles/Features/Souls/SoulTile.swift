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

    @ScaledMetric(relativeTo: .subheadline) private var tileHeight: CGFloat = 88

    var body: some View {
        let chosen = isSelected && showsSelection
        Button(action: onTap) {
            VStack(spacing: Spacing.xs + 2) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 44,
                               strokeColor: chosen ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .overlay(alignment: .topTrailing) {
                        if chosen { badge }
                    }
                Text(verbatim: soul.name)
                    .pebblesFont(chosen ? .subheadEmphasized : .subhead)
                    .foregroundStyle(chosen ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: tileHeight)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(chosen ? Color.pebbles.primary.opacity(0.20) : Color.clear)
            )
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: soul.name))
        .accessibilityAddTraits(chosen ? [.isSelected] : [])
    }

    private var badge: some View {
        PbblsGlyphView(.checkmarkDot, size: 13, style: AnyShapeStyle(Color.pebbles.onPrimary))
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
