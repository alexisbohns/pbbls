import Foundation
import Testing
@testable import Pebbles

@Suite("CaptureFlowModel")
@MainActor
struct CaptureFlowModelTests {

    private final class Recorder { var taps: [TapHaptic] = [] }

    private func model() -> (CaptureFlowModel, Recorder) {
        let recorder = Recorder()
        return (CaptureFlowModel(haptic: { recorder.taps.append($0) }), recorder)
    }

    @Test("photo and when always advance; name is the last step")
    func advance() {
        let (model, recorder) = model()
        #expect(model.optionalButtonIsSkip)
        model.advance()
        #expect(model.step == .when)
        model.advance()
        #expect(model.step == .name)
        model.advance()
        #expect(model.step == .name)
        #expect(recorder.taps == [.advance, .advance])
    }

    @Test("savable with a name or a photo, not with neither (D3)")
    func canSave() {
        let (model, _) = model()
        #expect(!model.canSave)
        model.setName("   ")
        #expect(!model.canSave)
        model.hasSnap = true
        #expect(model.canSave)
        model.hasSnap = false
        model.setName("Coffee")
        #expect(model.canSave)
    }

    @Test("the name is clamped to 40 characters")
    func clamp() {
        let (model, _) = model()
        model.setName(String(repeating: "a", count: 55))
        #expect(model.draft.name.count == CaptureFlowModel.nameLimit)
    }

    @Test("the photo's capture date seeds when; nil leaves now")
    func captureDate() {
        let (model, _) = model()
        let before = model.draft.happenedAt
        model.applyCaptureDate(nil)
        #expect(model.draft.happenedAt == before)
        let exif = Date(timeIntervalSince1970: 1_700_000_000)
        model.applyCaptureDate(exif)
        #expect(model.draft.happenedAt == exif)
    }

    @Test("a date the user set is never overridden by the photo's")
    func editedDateWins() {
        let (model, _) = model()
        let edited = Date(timeIntervalSince1970: 1_600_000_000)
        model.setHappenedAt(edited)
        #expect(model.hasEditedDate)
        model.applyCaptureDate(Date(timeIntervalSince1970: 1_700_000_000))
        #expect(model.draft.happenedAt == edited)
    }

    @Test("save outcomes: success haptic, failure keeps the error and stays")
    func outcomes() {
        let (model, recorder) = model()
        model.go(to: .name)
        model.beginSaving()
        #expect(model.isSaving)
        model.fail("nope")
        #expect(!model.isSaving)
        #expect(model.saveError == "nope")
        model.beginSaving()
        #expect(model.saveError == nil)
        model.succeed()
        #expect(recorder.taps == [.warning, .success])
    }
}
