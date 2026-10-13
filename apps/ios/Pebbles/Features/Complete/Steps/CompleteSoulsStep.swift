import SwiftUI

/// Who was there. Multi-select and optional, with the create tile; the
/// step's `Skip` / `Done` button advances (A1).
struct CompleteSoulsStep: View {
    let model: CompleteFlowModel

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
                // Created for this pebble, so it arrives selected.
                model.toggleSoul(inserted.id)
                Task { await refs.refreshSouls() }
            }
        }
    }
}
