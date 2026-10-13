import SwiftUI

/// How big and how bright (A1). Commits in place and arrives parked on
/// neutral-medium; `Continue` advances.
struct CompleteValenceStep: View {
    let model: CompleteFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { picked in
            model.select(valence: picked)
        }
        .onAppear { model.seedValenceIfNeeded() }
    }
}
