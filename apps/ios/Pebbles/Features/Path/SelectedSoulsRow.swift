import SwiftUI

/// Three-column grid of soul pills shown inside `PebbleFormView`'s "Souls"
/// section. Each selected soul renders as a bordered `SoulPill`. A
/// trailing dashed `AddSoulPill` opens `SoulPickerSheet`. Tapping any
/// pill (selected or "Add") opens the picker — selection is managed
/// exclusively there.
struct SelectedSoulsRow: View {
    @Binding var soulIds: [UUID]
    let allSouls: [SoulWithGlyph]

    @State private var isPresentingPicker = false

    var body: some View {
        LazyVGrid(columns: SoulPillGrid.columns, spacing: SoulPillGrid.spacing) {
            ForEach(selectedSouls) { soul in
                Button {
                    isPresentingPicker = true
                } label: {
                    SoulPill(glyph: soul.glyph, name: soul.name)
                }
                .buttonStyle(.plain)
            }

            Button {
                isPresentingPicker = true
            } label: {
                AddSoulPill()
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 8)
        .sheet(isPresented: $isPresentingPicker) {
            SoulPickerSheet(
                currentSelection: soulIds,
                onConfirm: { soulIds = $0 }
            )
        }
    }

    /// `selectedSouls` preserves the order of `soulIds` so pill order is
    /// stable across rerenders. Souls missing from `allSouls` (e.g. the
    /// loader hasn't returned yet) are dropped silently — they'll appear
    /// once the fetch completes.
    private var selectedSouls: [SoulWithGlyph] {
        let byId = Dictionary(uniqueKeysWithValues: allSouls.map { ($0.id, $0) })
        return soulIds.compactMap { byId[$0] }
    }
}
