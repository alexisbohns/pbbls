import SwiftUI

/// Step 3 — how big and how bright.
///
/// Unlike the other tile steps this one does not advance on pick: the fan is a
/// comparison and the roll is continuous, so `Continue` does the advancing. It
/// arrives parked on neutral-medium so the roll has something to roll.
struct RecordValenceStep: View {
    let model: RecordFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { picked in
            model.select(valence: picked)
        }
        .onAppear { model.seedValenceIfNeeded() }
    }
}
