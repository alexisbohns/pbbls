import Foundation
import Observation

/// The Capture flow's state machine (M58 D4 discipline: every interaction is
/// a method here and fires its haptic).
@Observable
@MainActor
final class CaptureFlowModel {

    /// Front-end only, as in the M58 flow.
    static let nameLimit = 40
    /// Front-end only, as the name. Three visible lines with a counter.
    static let descriptionLimit = 280

    enum WhenSource: Equatable { case now, photo, edited }

    var draft = CaptureDraft()
    private(set) var step: CaptureStep = .photo
    /// Mirrored from the `SnapUploadCoordinator` by the view.
    var hasSnap = false
    private(set) var isSaving = false
    private(set) var saveError: String?
    /// Set once the user changes the date themselves: a photo's capture date
    /// then never overrides their choice.
    private(set) var hasEditedDate = false
    /// Whether the when chip's date came from the photo.
    private(set) var seededFromPhoto = false

    var whenSource: WhenSource {
        if hasEditedDate { return .edited }
        return seededFromPhoto ? .photo : .now
    }

    private let haptic: @MainActor (TapHaptic) -> Void

    /// `startingAt: .moment` is the quick moment (D13): the page without the
    /// photo screen. A restored draft lands on the page either way.
    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) },
         startingAt step: CaptureStep = .photo) {
        self.haptic = haptic
        self.step = step
    }

    /// D3: a moment needs a name or a photo.
    var canSave: Bool { hasSnap || MomentName.display(draft.name) != nil }

    func advance() {
        guard let next = step.next else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    func go(to step: CaptureStep) {
        self.step = step
    }

    func setName(_ raw: String) {
        draft.name = String(raw.prefix(Self.nameLimit))
    }

    func setDescription(_ raw: String) {
        draft.description = String(raw.prefix(Self.descriptionLimit))
    }

    /// A draft from `CaptureDraftStore` after a kill (D2): straight to the page.
    func restore(_ saved: CaptureDraft, hasEditedDate: Bool) {
        draft = saved
        self.hasEditedDate = hasEditedDate
        step = .moment
    }

    /// The user's own edit of the when chip.
    func setHappenedAt(_ date: Date) {
        hasEditedDate = true
        draft.happenedAt = date
    }

    /// Seeds when from the picked photo's EXIF date, unless the user already
    /// set it.
    func applyCaptureDate(_ date: Date?) {
        guard let date, !hasEditedDate else { return }
        seededFromPhoto = true
        draft.happenedAt = date
    }

    func beginSaving() {
        isSaving = true
        saveError = nil
    }

    func succeed() {
        haptic(.success)
        isSaving = false
    }

    func fail(_ message: String) {
        haptic(.warning)
        isSaving = false
        saveError = message
    }
}
