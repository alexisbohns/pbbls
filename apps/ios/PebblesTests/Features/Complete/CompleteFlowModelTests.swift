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

    @Test("the valence step arrives empty and Continue is gated on a pick")
    func noSeed() {
        let (model, recorder) = model()
        #expect(model.step == .valence)
        #expect(model.draft.valence == nil)
        #expect(!model.isAnswered)
        model.advance()
        #expect(model.step == .valence)
        #expect(recorder.taps == [.warning])
        model.select(valence: .highlightMedium)
        #expect(model.isAnswered)
        model.advance()
        #expect(model.step == .emotion)
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

    @Test("setSouls mirrors the chooser's order without a haptic of its own; advance still moves on")
    func setSouls() {
        let (model, recorder) = model()
        model.go(to: .souls)
        let first = UUID()
        let second = UUID()
        model.setSouls([first, second])
        #expect(model.draft.soulIds == [first, second])
        model.setSouls([first, second])
        model.setSouls([second, first])
        #expect(model.draft.soulIds == [second, first])
        #expect(recorder.taps.isEmpty)
        model.advance()
        #expect(model.step == .collection)
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

    @Test("the autosave is the draft, nothing is held back")
    func autosaveIsDraft() {
        let (model, _) = model()
        #expect(model.draftToAutosave.isEmpty)
        model.select(valence: .lowlightSmall)
        #expect(model.draftToAutosave == model.draft)
        #expect(model.draftToAutosave.valence == .lowlightSmall)
    }

    @Test("a restore without a valence lands on an empty valence step")
    func restoreWithoutValence() {
        let (model, _) = model()
        var noValence = CompleteDraft()
        noValence.soulIds = [UUID()]
        let soul = noValence.soulIds[0]
        model.restore(noValence, known: .init(soulIds: [soul], collectionIds: []))
        #expect(model.step == .valence)
        #expect(model.draft.valence == nil)
        #expect(model.draftToAutosave.soulIds == [soul])
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

    @Test("quiet success does not buzz")
    func quietSuccessIsSilent() {
        let (model, recorder) = model()
        model.beginCompleting()
        let result = CompletionResult(pebbleId: UUID(), karmaDelta: 4, glyph: nil)
        model.succeed(with: result, quiet: true)
        #expect(model.step == .success)
        #expect(model.completed == result)
        #expect(!recorder.taps.contains(.success))
    }
}
