import Foundation
import Observation

/// What a completion produced, for the success screen (A4).
struct CompletionResult: Equatable {
    let pebbleId: UUID
    let karmaDelta: Int?
    let glyph: PebbleGlyph?
}

/// The Complete flow's state machine. Views own no flow state: a tap that
/// does not go through a method here changes nothing, and every method here
/// fires its haptic (M58 D4). The haptic is injected so tests can record it.
@Observable
@MainActor
final class CompleteFlowModel {

    var draft = CompleteDraft()
    private(set) var step: CompleteStep = .valence
    private(set) var completed: CompletionResult?
    private(set) var isCompleting = false
    private(set) var completeError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    // MARK: Answers

    /// Exhaustive with no `default`: the one place that says what answered means.
    func hasAnswer(for step: CompleteStep) -> Bool {
        switch step {
        case .privacy, .success: return true
        case .valence: return draft.valence != nil
        case .emotion: return draft.emotionId != nil
        case .domain: return draft.domainId != nil
        case .souls: return !draft.soulIds.isEmpty
        case .collection: return draft.collectionId != nil
        }
    }

    var isAnswered: Bool { step.isOptional || hasAnswer(for: step) }

    var optionalButtonIsSkip: Bool { !hasAnswer(for: step) }

    // MARK: Navigation

    func advance() {
        guard step != .success else { return }
        guard isAnswered else {
            haptic(.warning)
            return
        }
        guard let next = step.next, next != .success else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard step != .success, let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    /// Jump without gating or feedback: restore and tests only.
    func go(to step: CompleteStep) {
        self.step = step
    }

    // MARK: Selection

    private func commitAndAdvance(_ mutate: (inout CompleteDraft) -> Void) {
        haptic(.selection)
        mutate(&draft)
        if let next = step.next, next != .success { step = next }
    }

    func seedValenceIfNeeded() {
        guard draft.valence == nil else { return }
        draft.valence = .neutralMedium
    }

    func select(valence: Valence) {
        haptic(.selection)
        draft.valence = valence
    }

    func select(emotionId: UUID) { commitAndAdvance { $0.emotionId = emotionId } }
    func select(domainId: UUID) { commitAndAdvance { $0.domainId = domainId } }
    func select(collectionId: UUID) { commitAndAdvance { $0.collectionId = collectionId } }

    func toggleSoul(_ id: UUID) {
        haptic(.selection)
        if let index = draft.soulIds.firstIndex(of: id) {
            draft.soulIds.remove(at: index)
        } else {
            draft.soulIds.append(id)
        }
    }

    func select(visibility: Visibility) {
        haptic(.selection)
        draft.visibility = visibility
    }

    // MARK: Restore (A3)

    func firstGap() -> CompleteStep {
        for candidate in CompleteStep.counted where !candidate.isOptional {
            if !hasAnswer(for: candidate) { return candidate }
        }
        return .privacy
    }

    func restore(_ saved: CompleteDraft, known: CompleteDraft.KnownIds) {
        draft = saved.sanitized(known: known)
        step = firstGap()
    }

    // MARK: Completing

    func beginCompleting() {
        isCompleting = true
        completeError = nil
    }

    func succeed(with result: CompletionResult) {
        haptic(.success)
        isCompleting = false
        completed = result
        step = .success
    }

    func fail(_ message: String) {
        haptic(.warning)
        isCompleting = false
        completeError = message
    }
}
