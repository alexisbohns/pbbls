import Foundation
import Observation

/// The record flow's state machine: the draft under construction, the step the
/// user is on, and every interaction that changes either (D4).
///
/// Views own no flow state. That is not tidiness for its own sake — the
/// requirement is a haptic on *every* tap, and implemented as a discipline
/// ("remember to buzz in each action closure") it is one forgotten closure
/// away from being false, and untestable besides. Routing every interaction
/// through this type makes it structural: a tap that does not call a method
/// here changes nothing, and every method here buzzes.
///
/// The haptic is injected so tests can record it without touching UIKit.
@Observable
@MainActor
final class RecordFlowModel {

    /// Longest name the flow accepts. Front-end only (D3): neither
    /// `pebbles.name` nor `PebbleCreatePayload` constrains length, and nothing
    /// server-side is added to enforce it.
    static let nameLimit = 40

    var draft = PebbleDraft()
    private(set) var step: RecordStep = .photo

    /// Mirrored from the `SnapUploadCoordinator` by the view, so the photo
    /// step's button can read `Skip` or `Done` without the model owning media.
    var hasSnap: Bool = false

    /// Set once publish returns. Drives the success step.
    private(set) var published: ComposePebbleResponse?
    private(set) var isPublishing = false
    private(set) var publishError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    // MARK: - Answers

    /// Whether a given step has been answered. Drives both the forward gate and
    /// the optional steps' `Skip` / `Done` button label.
    ///
    /// Exhaustive with no `default`, deliberately: this is the single place
    /// that says what "answered" means, and a `default` would silently treat a
    /// newly added step as already answered.
    func hasAnswer(for step: RecordStep) -> Bool {
        switch step {
        // Nothing for the user to supply: `when` arrives seeded from the
        // photo's EXIF or from now, `privacy` from `.secret`, and `success` is
        // terminal.
        case .when, .privacy, .success:
            return true
        case .photo:
            return hasSnap
        case .name:
            return !draft.name.trimmingCharacters(in: .whitespaces).isEmpty
        case .valence:
            return draft.valence != nil
        case .emotion:
            return draft.emotionId != nil
        case .domain:
            return draft.domainId != nil
        case .souls:
            return !draft.soulIds.isEmpty
        case .collection:
            return draft.collectionId != nil
        case .glyph:
            return draft.glyphId != nil
        }
    }

    /// Whether the current step may be left. Optional steps are always
    /// satisfied: passing one is the user saying "not this one", not an error.
    var isAnswered: Bool {
        step.isOptional || hasAnswer(for: step)
    }

    /// `Skip` while the optional step is empty, `Done` once it holds something.
    /// Only meaningful on optional steps.
    var optionalButtonIsSkip: Bool {
        !hasAnswer(for: step)
    }

    // MARK: - Navigation

    func advance() {
        guard step != .success else { return }
        guard isAnswered else {
            haptic(.warning)
            return
        }
        guard let next = step.next else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard step != .success, let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    /// Jump without gating or feedback. For resume and for tests — never wired
    /// to a control.
    func go(to step: RecordStep) {
        self.step = step
    }

    // MARK: - Selection

    /// A tile pick: commit the value and move on.
    ///
    /// Fires `.selection` and nothing else. The pick and the advance are one
    /// gesture, and two buzzes for one tap reads as a stutter rather than as
    /// two pieces of information.
    private func commitAndAdvance(_ mutate: (inout PebbleDraft) -> Void) {
        haptic(.selection)
        mutate(&draft)
        if let next = step.next { step = next }
    }

    /// The valence step arrives already parked on a value: the roll needs
    /// something under the finger, and an empty roll has no affordances to
    /// read. Seeds without a haptic — nothing happened yet that the user did.
    func seedValenceIfNeeded() {
        guard draft.valence == nil else { return }
        draft.valence = .neutralMedium
    }

    /// Valence commits in place instead of advancing: the fan is a
    /// comparison, and a tap that leaves the screen denies the user the look
    /// at what they just chose next to the eight they did not. The step's
    /// `Continue` button does the advancing.
    func select(valence: Valence) {
        haptic(.selection)
        draft.valence = valence
    }

    func select(emotionId: UUID) { commitAndAdvance { $0.emotionId = emotionId } }
    func select(domainId: UUID) { commitAndAdvance { $0.domainId = domainId } }
    func select(collectionId: UUID) { commitAndAdvance { $0.collectionId = collectionId } }
    func select(glyphId: UUID) { commitAndAdvance { $0.glyphId = glyphId } }

    /// Souls are multi-select, so a toggle never advances — the step's
    /// `Skip` / `Done` button does that.
    func toggleSoul(_ id: UUID) {
        haptic(.selection)
        if let index = draft.soulIds.firstIndex(of: id) {
            draft.soulIds.remove(at: index)
        } else {
            draft.soulIds.append(id)
        }
    }

    /// Privacy selects without advancing: `Publish` is the step's action, and
    /// silently publishing on a grade tap would be a trap.
    func select(visibility: Visibility) {
        haptic(.selection)
        draft.visibility = visibility
    }

    /// Clamped write for the name field (D3).
    func setName(_ raw: String) {
        draft.name = String(raw.prefix(Self.nameLimit))
    }

    /// Seed the date from the picked photo's EXIF (D7). No-op for a nil date,
    /// so a photo without metadata leaves `happenedAt` at its default of now.
    func applyCaptureDate(_ date: Date?) {
        guard let date else { return }
        draft.happenedAt = date
    }

    // MARK: - Resume

    /// The first mandatory step this draft has not answered — where a resumed
    /// draft lands (D9).
    ///
    /// Optional steps never count as gaps: skipping one is a legitimate answer,
    /// and re-asking would silently undo the user's decision.
    func firstGap() -> RecordStep {
        for candidate in RecordStep.counted where !candidate.isOptional {
            if !hasAnswer(for: candidate) { return candidate }
        }
        return .privacy
    }

    func resume(from payload: PebbleDraftPayload, known: PebbleDraft.KnownIds) {
        draft = PebbleDraft(payload: payload, known: known)
        step = firstGap()
    }

    // MARK: - Publish

    func beginPublish() {
        isPublishing = true
        publishError = nil
    }

    func succeed(with response: ComposePebbleResponse) {
        haptic(.success)
        isPublishing = false
        published = response
        step = .success
    }

    /// A hard failure never reaches the success step (D10): the flow stays put
    /// so the draft — and the way out via `✕` → Save as draft — is untouched.
    func fail(_ message: String) {
        haptic(.warning)
        isPublishing = false
        publishError = message
    }
}
