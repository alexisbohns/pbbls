import Foundation
import Observation

/// The Capture flow's state machine (M58 D4 discipline: every interaction is
/// a method here and fires its haptic).
@Observable
@MainActor
final class CaptureFlowModel {

    /// Front-end only, as in the M58 flow.
    static let nameLimit = 40

    var draft = CaptureDraft()
    private(set) var step: CaptureStep = .photo
    /// Mirrored from the `SnapUploadCoordinator` by the view.
    var hasSnap = false
    private(set) var isSaving = false
    private(set) var saveError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    /// D3: a moment needs a name or a photo.
    var canSave: Bool { hasSnap || MomentName.display(draft.name) != nil }

    /// `Skip` on the photo step until a photo is picked, then `Done`.
    var optionalButtonIsSkip: Bool { step == .photo && !hasSnap }

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

    func applyCaptureDate(_ date: Date?) {
        guard let date else { return }
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
