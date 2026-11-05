import SwiftUI

/// How big and how bright (A1). Commits in place and arrives empty (D7);
/// `Continue` advances once a stone is chosen.
struct CompleteValenceStep: View {
    let model: CompleteFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { picked in
            model.select(valence: picked)
        }
    }
}
