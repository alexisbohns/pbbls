import SwiftUI

/// Step 6 — who was there. Multi-select, so a tap never advances; the step's
/// `Skip` / `Done` button does (D3).
///
/// Reads `ReferenceDataService.souls` rather than fetching its own list the way
/// `SoulPickerSheet` does: that cache is already refreshed after every Profile
/// mutation, and a freshly created soul is picked up by refreshing it here.
struct RecordSoulsStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @State private var isPresentingCreate = false

    var body: some View {
        SoulPickerContent(
            souls: refs.souls,
            selection: Set(model.draft.soulIds),
            onToggle: { model.toggleSoul($0) },
            onCreate: { isPresentingCreate = true }
        )
        .sheet(isPresented: $isPresentingCreate) {
            CreateSoulSheet { inserted in
                // Select it immediately — the user created it *for* this
                // pebble, so making them tap it again is friction.
                model.toggleSoul(inserted.id)
                Task { await refs.refreshSouls() }
            }
        }
    }
}
