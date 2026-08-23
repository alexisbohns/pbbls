import SwiftUI

/// Step 4 — the emotion. Categories arrive ordered by the valence chosen on
/// step 3, which is the reason valence comes first (D2).
///
/// Unlike `EmotionPickerSheet` there is no staging and no toggle-to-clear: a
/// step that advances on tap cannot be cancelled, so the tap is the commit.
struct RecordEmotionStep: View {
    let model: RecordFlowModel

    var body: some View {
        EmotionPickerContent(
            selected: model.draft.emotionId,
            valence: model.draft.valence
        ) { picked in
            model.select(emotionId: picked)
        }
    }
}
