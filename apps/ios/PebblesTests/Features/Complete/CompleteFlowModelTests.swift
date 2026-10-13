import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteFlowModel")
@MainActor
struct CompleteFlowModelTests {

    private final class Recorder { var taps: [TapHaptic] = [] }

    private func model() -> (CompleteFlowModel, Recorder) {
        let recorder = Recorder()
        return (CompleteFlowModel(haptic: { recorder.taps.append($0) }), recorder)
    }

    @Test("starts on valence; seeding parks on neutral-medium without a haptic")
    func seed() {
        let (model, recorder) = model()
        #expect(model.step == .valence)
        #expect(!model.isAnswered)
        model.seedValenceIfNeeded()
        #expect(model.draft.valence == .neutralMedium)
        #expect(recorder.taps.isEmpty)
    }

    @Test("valence commits in place; emotion and domain commit and advance")
    func tiles() {
        let (model, recorder) = model()
        model.select(valence: .highlightLarge)
        #expect(model.step == .valence)
        model.advance()
        #expect(model.step == .emotion)
        model.select(emotionId: UUID())
        #expect(model.step == .domain)
        model.select(domainId: UUID())
        #expect(model.step == .souls)
        #expect(recorder.taps == [.selection, .advance, .selection, .selection])
    }

    @Test("a mandatory step blocks advance with a warning")
    func gated() {
        let (model, recorder) = model()
        model.advance()
        #expect(model.step == .valence)
        #expect(recorder.taps == [.warning])
    }

    @Test("souls toggle without advancing; collection commits and advances")
    func optionalSteps() {
        let (model, _) = model()
        model.go(to: .souls)
        #expect(model.optionalButtonIsSkip)
        let soul = UUID()
        model.toggleSoul(soul)
        #expect(model.step == .souls)
        #expect(!model.optionalButtonIsSkip)
        model.toggleSoul(soul)
        #expect(model.draft.soulIds.isEmpty)
        model.advance()
        #expect(model.step == .collection)
        model.select(collectionId: UUID())
        #expect(model.step == .privacy)
    }

    @Test("privacy selects without advancing; back preserves answers")
    func privacyAndBack() {
        let (model, _) = model()
        model.go(to: .privacy)
        model.select(visibility: .public)
        #expect(model.step == .privacy)
        model.back()
        #expect(model.step == .collection)
        #expect(model.draft.visibility == .public)
    }

    @Test("restore sanitizes and lands on the first unanswered mandatory step")
    func restore() {
        let (model, _) = model()
        var saved = CompleteDraft()
        saved.valence = .neutralSmall
        saved.emotionId = UUID()
        saved.soulIds = [UUID()]
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.step == .domain)
        #expect(model.draft.soulIds.isEmpty)
        saved.domainId = UUID()
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.step == .privacy)
    }

    @Test("an unconfirmed seed is not autosaved; Continue or a pick confirms it")
    func seedNotAutosaved() {
        let (model, _) = model()
        model.seedValenceIfNeeded()
        #expect(!model.isValenceConfirmed)
        #expect(model.draftToAutosave.isEmpty)
        #expect(model.draft.valence == .neutralMedium)
        model.advance()
        #expect(model.isValenceConfirmed)
        #expect(model.draftToAutosave.valence == .neutralMedium)

        let (picked, _) = self.model()
        picked.seedValenceIfNeeded()
        picked.select(valence: .lowlightSmall)
        #expect(picked.isValenceConfirmed)
        #expect(picked.draftToAutosave.valence == .lowlightSmall)
    }

    @Test("a restored valence counts as confirmed; a restore without one re-seeds")
    func restoreConfirmation() {
        let (model, _) = model()
        var saved = CompleteDraft()
        saved.valence = .neutralSmall
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.isValenceConfirmed)
        #expect(model.draftToAutosave.valence == .neutralSmall)

        let (unseeded, _) = self.model()
        var noValence = CompleteDraft()
        noValence.soulIds = [UUID()]
        let soul = noValence.soulIds[0]
        unseeded.restore(noValence, known: .init(soulIds: [soul], collectionIds: []))
        #expect(unseeded.step == .valence)
        #expect(unseeded.draft.valence == .neutralMedium)
        #expect(!unseeded.isValenceConfirmed)
        #expect(unseeded.draftToAutosave.valence == nil)
        #expect(unseeded.draftToAutosave.soulIds == [soul])
    }

    @Test("success goes to the success step with a success haptic; failure stays put")
    func outcomes() {
        let (model, recorder) = model()
        model.go(to: .privacy)
        model.beginCompleting()
        model.fail("nope")
        #expect(model.step == .privacy)
        #expect(model.completeError == "nope")
        model.beginCompleting()
        #expect(model.completeError == nil)
        let result = CompletionResult(pebbleId: UUID(), karmaDelta: 4, glyph: nil)
        model.succeed(with: result)
        #expect(model.step == .success)
        #expect(model.completed == result)
        #expect(recorder.taps.suffix(2) == [.warning, .success])
    }
}
