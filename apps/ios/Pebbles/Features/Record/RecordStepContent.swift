import PhotosUI
import SwiftUI

// See RecordStep+Copy.swift: an exhaustive per-step switch is a dispatch
// table, and losing exhaustiveness would mean a new step silently renders
// nothing.
/// Renders the body of whichever step the flow is on.
///
/// Split out of `RecordFlowView` so the container keeps to chrome, the action
/// table and orchestration. Holds no state of its own — every input arrives as
/// a parameter, so the whole flow's state stays in one place.
struct RecordStepContent: View {
    let step: RecordStep
    let model: RecordFlowModel
    let snap: FormSnap?
    /// True when the `when` step's date came from the photo's EXIF (D7).
    let seededFromPhoto: Bool
    /// Non-nil while the attached photo blocks publishing.
    let snapBlockedMessage: String?
    @Binding var selectedGlyph: Glyph?
    /// The photo step's inline picker selection (owned by `RecordFlowView`).
    @Binding var pickedPhoto: PhotosPickerItem?

    let onRetryPhoto: () -> Void
    let onRemovePhoto: () -> Void

    var body: some View {
        switch step {
        case .photo:
            RecordPhotoStep(
                snap: snap,
                selection: $pickedPhoto,
                onRetry: onRetryPhoto,
                onRemove: onRemovePhoto
            )
        case .when:
            RecordWhenStep(
                happenedAt: Binding(
                    get: { model.draft.happenedAt },
                    set: { model.draft.happenedAt = $0 }
                ),
                seededFromPhoto: seededFromPhoto
            )
        case .name:
            RecordNameStep(
                name: model.draft.name,
                limit: RecordFlowModel.nameLimit,
                onChange: { model.setName($0) }
            )
        case .valence:    RecordValenceStep(model: model)
        case .emotion:    RecordEmotionStep(model: model)
        case .domain:     RecordDomainStep(model: model)
        case .souls:      RecordSoulsStep(model: model)
        case .collection: RecordCollectionStep(model: model)
        case .glyph:      RecordGlyphStep(model: model, selectedGlyph: $selectedGlyph)
        case .privacy:    RecordPrivacyStep(model: model, snapBlockedMessage: snapBlockedMessage)
        // Handled by RecordFlowView, which renders the success step outside the
        // scaffold so it can own its full-screen layout.
        case .success:    EmptyView()
        }
    }
}
