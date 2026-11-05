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

    @Test("valence commits in place; emotion commits and advances to life")
    func tiles() {
        let (model, recorder) = model()
        model.select(valence: .highlightLarge)
        #expect(model.step == .valence)
        model.advance()
        #expect(model.step == .emotion)
        model.select(emotionId: UUID())
        #expect(model.step == .life)
        #expect(recorder.taps == [.selection, .advance, .selection])
    }

    @Test("domains toggle up to three, in tap order; the fourth is refused with a warning")
    func toggleDomain() {
        let (model, recorder) = model()
        let ids = (0..<4).map { _ in UUID() }
        ids.forEach { model.toggleDomain($0) }
        #expect(model.draft.domainIds == Array(ids.prefix(3)))
        #expect(recorder.taps.last == .warning)
        model.toggleDomain(ids[0])
        #expect(model.draft.domainIds == [ids[1], ids[2]])
    }

    @Test("life is answered with a domain; souls are optional")
    func lifeAnswered() {
        let (model, _) = model()
        model.go(to: .life)
        #expect(!model.isAnswered)
        model.toggleSoul(UUID())
        #expect(!model.isAnswered)
        model.toggleDomain(UUID())
        #expect(model.isAnswered)
    }

    @Test("a mandatory step blocks advance with a warning")
    func gated() {
        let (model, recorder) = model()
        model.advance()
        #expect(model.step == .valence)
        #expect(recorder.taps == [.warning])
    }

    @Test("souls toggle on the life step without advancing; collection selects in place")
    func optionalPicks() {
        let (model, recorder) = model()
        model.go(to: .life)
        let soul = UUID()
        model.toggleSoul(soul)
        #expect(model.step == .life)
        #expect(model.draft.soulIds == [soul])
        model.toggleSoul(soul)
        #expect(model.draft.soulIds.isEmpty)
        let collection = UUID()
        model.select(collectionId: collection)
        #expect(model.step == .life)
        #expect(model.draft.collectionId == collection)
        model.select(collectionId: nil)
        #expect(model.draft.collectionId == nil)
        #expect(recorder.taps == [.selection, .selection, .selection, .selection])
    }

    @Test("setSouls mirrors the chooser's order without a haptic of its own; life never advances past itself")
    func setSouls() {
        let (model, recorder) = model()
        model.go(to: .life)
        let first = UUID()
        let second = UUID()
        model.setSouls([first, second])
        #expect(model.draft.soulIds == [first, second])
        model.setSouls([first, second])
        model.setSouls([second, first])
        #expect(model.draft.soulIds == [second, first])
        #expect(recorder.taps.isEmpty)
        model.toggleDomain(UUID())
        model.advance()
        #expect(model.step == .life)
    }

    @Test("visibility selects without advancing; back preserves answers")
    func visibilityAndBack() {
        let (model, _) = model()
        model.go(to: .life)
        model.select(visibility: .public)
        #expect(model.step == .life)
        model.back()
        #expect(model.step == .emotion)
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
        #expect(model.step == .life)
        #expect(model.draft.soulIds.isEmpty)
        saved.domainIds = [UUID()]
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.step == .life)
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
        model.go(to: .life)
        model.beginCompleting()
        model.fail("nope")
        #expect(model.step == .life)
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
