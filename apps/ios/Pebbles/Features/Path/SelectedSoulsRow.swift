import SwiftUI

/// Inline chip flow shown inside `PebbleFormView`'s "Souls" section. Each
/// selected soul renders as a `SoulChip` (rounded-square glyph + name).
/// A trailing dashed "Add" chip opens `SoulPickerSheet`. Tapping any chip
/// also opens the picker — selection is managed exclusively there.
struct SelectedSoulsRow: View {
    @Binding var soulIds: [UUID]
    let allSouls: [SoulWithGlyph]

    @State private var isPresentingPicker = false

    var body: some View {
        PebblePillFlow(spacing: 12) {
            ForEach(selectedSouls) { soul in
                Button {
                    isPresentingPicker = true
                } label: {
                    SoulChip(soul: soul)
                }
                .buttonStyle(.plain)
            }

            Button {
                isPresentingPicker = true
            } label: {
                AddSoulChip()
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .sheet(isPresented: $isPresentingPicker) {
            SoulPickerSheet(
                currentSelection: soulIds,
                onConfirm: { soulIds = $0 }
            )
        }
    }

    /// `selectedSouls` preserves the order of `soulIds` so chip order is
    /// stable across rerenders. Souls missing from `allSouls` (e.g. the
    /// loader hasn't returned yet) are dropped silently — they'll appear
    /// once the fetch completes.
    private var selectedSouls: [SoulWithGlyph] {
        let byId = Dictionary(uniqueKeysWithValues: allSouls.map { ($0.id, $0) })
        return soulIds.compactMap { byId[$0] }
    }
}

/// Selected soul: 44pt rounded-square glyph thumbnail with a muted accent
/// background, name label to the right.
private struct SoulChip: View {
    let soul: SoulWithGlyph

    var body: some View {
        HStack(spacing: 8) {
            GlyphThumbnail(strokes: soul.glyph.strokes, side: 32)
                .padding(6)
                .background(Color.pebblesSurfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            Text(soul.name)
                .font(.subheadline)
                .foregroundStyle(Color.pebblesForeground)
                .lineLimit(1)
        }
        .padding(.trailing, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
    }
}

/// Trailing dashed "Add" chip. Same height as a `SoulChip` so the flow
/// aligns vertically.
private struct AddSoulChip: View {
    var body: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1.5, dash: [4])
                )
                .frame(width: 44, height: 44)
                .overlay {
                    Image(systemName: "person.badge.plus")
                        .font(.callout)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
            Text("Add")
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)
                .lineLimit(1)
        }
        .padding(.trailing, 8)
        .accessibilityLabel("Add a soul")
    }
}
