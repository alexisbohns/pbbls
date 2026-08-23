import SwiftUI

/// Step 3 — how big and how bright. Commits on tap and advances (D3).
struct RecordValenceStep: View {
    let model: RecordFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { picked in
            model.select(valence: picked)
        }
    }
}
