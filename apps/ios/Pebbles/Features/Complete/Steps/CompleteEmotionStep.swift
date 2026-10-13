import SwiftUI

/// The emotion, its categories ordered by the valence picked first. The tap
/// is the commit and the advance (A1).
struct CompleteEmotionStep: View {
    let model: CompleteFlowModel

    var body: some View {
        EmotionPickerContent(
            selected: model.draft.emotionId,
            valence: model.draft.valence
        ) { picked in
            model.select(emotionId: picked)
        }
    }
}
