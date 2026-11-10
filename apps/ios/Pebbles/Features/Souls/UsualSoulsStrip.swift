import SwiftUI

/// "Who was there?" on the Life screen (record-flow D10, souls spec S5): the
/// four most-tagged people as tiles that toggle in place, and a More tile that
/// opens the picker sheet. With nobody at all, one "Add people" tile.
struct UsualSoulsStrip: View {
    let souls: [SoulWithGlyph]
    let selection: [UUID]
    let onToggle: (UUID) -> Void
    let onMore: () -> Void

    /// Matches `SoulTile`, so the More tile sits level with the people.
    @ScaledMetric(relativeTo: .subheadline) private var tileHeight: CGFloat = 88

    /// The usual four, with anyone chosen for this pebble but missing from
    /// them (a person created inline in the picker) taking the last slot, so
    /// a fresh pick is visible on return. Several such people share that one
    /// slot: only the latest shows, the others stay in the draft.
    private var shown: [SoulWithGlyph] {
        var tiles = Array(SoulChooserModel.usual(from: souls).prefix(4))
        for id in selection where !tiles.contains(where: { $0.id == id }) {
            if let soul = souls.first(where: { $0.id == id }) {
                if tiles.count == 4 { tiles.removeLast() }
                tiles.append(soul)
            }
        }
        return tiles
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs + 2) {
            Text("Who was there?")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            HStack(spacing: 4) {
                if souls.isEmpty {
                    moreTile(label: "Add people")
                } else {
                    ForEach(shown) { soul in
                        SoulTile(soul: soul, isSelected: selection.contains(soul.id)) { onToggle(soul.id) }
                    }
                    moreTile(label: "More")
                }
            }
        }
    }

    private func moreTile(label: LocalizedStringResource) -> some View {
        Button(action: onMore) {
            VStack(spacing: Spacing.xs + 2) {
                PbblsGlyphView(.plusCircle, size: 44, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: tileHeight)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1.5, dash: [6, 6])))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
