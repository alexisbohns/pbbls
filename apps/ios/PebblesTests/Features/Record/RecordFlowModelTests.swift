import Foundation
import Testing
@testable import Pebbles

/// Collects the haptics a model fires so tests can assert the mapping without
/// touching UIKit.
@MainActor
final class HapticRecorder {
    var played: [TapHaptic] = []
}

@MainActor
private func makeModel() -> (RecordFlowModel, HapticRecorder) {
    let recorder = HapticRecorder()
    let model = RecordFlowModel(haptic: { recorder.played.append($0) })
    return (model, recorder)
}

@Suite("RecordFlowModel — gating")
@MainActor
struct RecordFlowModelGatingTests {

    @Test("a mandatory step does not advance while unanswered, and warns")
    func mandatoryStepGates() {
        let (model, recorder) = makeModel()
        model.go(to: .valence)
        recorder.played.removeAll()

        model.advance()

        #expect(model.step == .valence)
        #expect(recorder.played == [.warning])
    }

    @Test("an optional step advances with nothing chosen")
    func optionalStepPasses() {
        let (model, recorder) = makeModel()
        model.go(to: .souls)
        recorder.played.removeAll()

        model.advance()

        #expect(model.step == .collection)
        #expect(recorder.played == [.advance])
    }

    @Test("the name step gates on non-whitespace text")
    func nameGates() {
        let (model, _) = makeModel()
        model.go(to: .name)

        model.setName("   ")
        model.advance()
        #expect(model.step == .name)

        model.setName("Ferry to Ithaca")
        model.advance()
        #expect(model.step == .valence)
    }

    @Test("when and privacy are pre-seeded, so they never gate")
    func preSeededStepsPass() {
        let (model, _) = makeModel()
        model.go(to: .when)
        model.advance()
        #expect(model.step == .name)

        model.go(to: .privacy)
        #expect(model.isAnswered)
    }
}

@Suite("RecordFlowModel — navigation")
@MainActor
struct RecordFlowModelNavigationTests {

    @Test("back preserves the answer already given")
    func backPreservesAnswers() {
        let (model, _) = makeModel()
        model.go(to: .valence)
        model.select(valence: .highlightMedium)
        // Valence commits in place; Continue is what advances.
        #expect(model.step == .valence)
        model.advance()
        #expect(model.step == .emotion)

        model.back()

        #expect(model.step == .valence)
        #expect(model.draft.valence == .highlightMedium)
    }

    @Test("back stops at the first step")
    func backTerminates() {
        let (model, _) = makeModel()
        #expect(model.step == .photo)
        model.back()
        #expect(model.step == .photo)
    }

    @Test("success is terminal — neither advance nor back moves off it")
    func successIsTerminal() {
        let (model, _) = makeModel()
        model.succeed(with: ComposePebbleResponse(
            pebbleId: UUID(), renderSvg: nil, renderVersion: nil, karmaDelta: 3
        ))
        #expect(model.step == .success)

        model.advance()
        model.back()

        #expect(model.step == .success)
    }
}

@Suite("RecordFlowModel — selection")
@MainActor
struct RecordFlowModelSelectionTests {

    @Test("a tile pick commits and advances on one selection haptic")
    func tilePickAdvances() {
        let (model, recorder) = makeModel()
        model.go(to: .domain)
        recorder.played.removeAll()
        let domainId = UUID()

        model.select(domainId: domainId)

        #expect(model.draft.domainId == domainId)
        #expect(model.step == .souls)
        // One tap, one buzz: a pick that also advances must not fire twice.
        #expect(recorder.played == [.selection])
    }

    @Test("souls toggle without advancing, and toggle off again")
    func soulsToggle() {
        let (model, _) = makeModel()
        model.go(to: .souls)
        let first = UUID()
        let second = UUID()

        model.toggleSoul(first)
        model.toggleSoul(second)
        #expect(model.draft.soulIds == [first, second])
        #expect(model.step == .souls)

        model.toggleSoul(first)
        #expect(model.draft.soulIds == [second])
    }

    @Test("privacy selects without advancing — publish is the step's action")
    func privacyDoesNotAdvance() {
        let (model, _) = makeModel()
        model.go(to: .privacy)

        model.select(visibility: .public)

        #expect(model.draft.visibility == .public)
        #expect(model.step == .privacy)
    }

    @Test("the name is clamped to 40 characters")
    func nameIsClamped() {
        let (model, _) = makeModel()
        model.setName(String(repeating: "a", count: 55))
        #expect(model.draft.name.count == RecordFlowModel.nameLimit)
        #expect(model.draft.name.count == 40)
    }
}

@Suite("RecordFlowModel — optional button label")
@MainActor
struct RecordFlowModelOptionalButtonTests {

    @Test("an untouched optional step offers Skip; a filled one offers Done")
    func labelFollowsAnswer() {
        let (model, _) = makeModel()
        model.go(to: .collection)
        #expect(model.optionalButtonIsSkip)

        model.draft.collectionId = UUID()
        #expect(model.optionalButtonIsSkip == false)
    }
}

@Suite("RecordFlowModel — resume")
@MainActor
struct RecordFlowModelResumeTests {

    /// A draft with a name but no valence: the first gap is valence, and the
    /// steps before it must not be re-asked.
    @Test("resume lands on the first unanswered mandatory step")
    func resumeLandsOnFirstGap() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.draft.name == "Ferry to Ithaca")
        #expect(model.step == .valence)
    }

    @Test("a fully answered draft resumes straight to privacy")
    func completeDraftResumesToPrivacy() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"
        payload.emotionId = UUID()
        payload.domainIds = [UUID()]
        payload.positiveness = 1
        payload.intensity = 2

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.step == .privacy)
    }

    @Test("skipped optional steps are never treated as gaps")
    func optionalStepsAreNotGaps() {
        let (model, _) = makeModel()
        var payload = PebbleDraftPayload()
        payload.name = "Ferry to Ithaca"
        payload.emotionId = UUID()
        payload.domainIds = [UUID()]
        payload.positiveness = 1
        payload.intensity = 2
        // No souls, no collection, no glyph, no snap — all legitimately skipped.

        model.resume(from: payload, known: .init(soulIds: [], collectionIds: []))

        #expect(model.step == .privacy)
    }
}

@Suite("RecordFlowModel — publish")
@MainActor
struct RecordFlowModelPublishTests {

    @Test("a successful publish celebrates and moves to the success step")
    func publishSucceeds() {
        let (model, recorder) = makeModel()
        model.go(to: .privacy)
        model.beginPublish()
        #expect(model.isPublishing)
        recorder.played.removeAll()

        let pebbleId = UUID()
        model.succeed(with: ComposePebbleResponse(
            pebbleId: pebbleId, renderSvg: "<svg/>", renderVersion: "v1", karmaDelta: 5
        ))

        #expect(model.isPublishing == false)
        #expect(model.step == .success)
        #expect(model.published?.pebbleId == pebbleId)
        #expect(model.published?.karmaDelta == 5)
        #expect(recorder.played == [.success])
    }

    @Test("a failed publish stays on privacy, warns, and keeps the draft")
    func publishFails() {
        let (model, recorder) = makeModel()
        model.go(to: .privacy)
        model.setName("Ferry to Ithaca")
        model.beginPublish()
        recorder.played.removeAll()

        model.fail("Photo upload failed. Retry or remove it.")

        #expect(model.step == .privacy)
        #expect(model.isPublishing == false)
        #expect(model.publishError == "Photo upload failed. Retry or remove it.")
        #expect(model.draft.name == "Ferry to Ithaca")
        #expect(recorder.played == [.warning])
    }

    @Test("beginning a publish clears the previous error")
    func retryClearsError() {
        let (model, _) = makeModel()
        model.fail("Couldn't save your pebble. Please try again.")
        model.beginPublish()
        #expect(model.publishError == nil)
    }
}
