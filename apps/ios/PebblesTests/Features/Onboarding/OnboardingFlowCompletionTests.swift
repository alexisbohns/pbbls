import Foundation
import Testing
@testable import Pebbles

/// Step 6 and the one write: Android's `completing sends every answer once…`, `a refused completion…`
/// and the completion half of `a person is never sent without a glyph`.
@Suite("OnboardingFlowModel completion")
@MainActor
struct OnboardingFlowCompletionTests {
    private struct Boom: Error {}

    private func addPerson(_ model: OnboardingFlowModel, _ name: String) {
        model.addPerson()
        let index = model.people.count - 1
        model.setPersonName(index, name)
        model.commitPerson(index)
    }

    /// From a fresh model to `pace` the way a person gets there, answers given on the way.
    private func modelAtPace(_ harness: OnboardingFlowHarness, withPerson: Bool = false) async -> OnboardingFlowModel {
        let model = harness.model()
        await model.loadPastConsent()
        model.toggleReason(.goodDays)
        model.toggleReason(.keepPeople)
        model.next()
        model.setName("Maya")
        model.pickGlyph(OnboardingFlowHarness.glyphs[2].id)
        model.next()
        if withPerson { addPerson(model, "Noé") }
        model.next()
        #expect(model.step.id == .pace)
        return model
    }

    @Test("completing sends every answer once and leaves the hand-off note")
    func completingSendsEverything() async {
        let harness = OnboardingFlowHarness()
        let model = await modelAtPace(harness, withPerson: true)
        model.selectPace(.daily)
        model.next()
        await model.work?.value
        let sent = harness.service.completeCalls
        #expect(sent.count == 1)
        #expect(sent.first?.displayName == "Maya")
        #expect(sent.first?.glyphId == OnboardingFlowHarness.glyphs[2].id)
        #expect(sent.first?.reasons == [.goodDays, .keepPeople])
        #expect(sent.first?.pace == .daily)
        #expect(sent.first?.souls == [OnboardingSoulDraft(name: "Noé", glyphId: OnboardingFlowHarness.glyphs[0].id)])
        #expect(model.doneNote == OnboardingHandoffNote(userId: "user-1", reason: .goodDays))
        #expect(model.isDone)
        #expect(!model.isCompleting)
    }

    @Test("a refused completion stays on pace, keeps every answer and a retry resends")
    func refusedStaysAndRetries() async {
        let harness = OnboardingFlowHarness()
        harness.service.completeFailure = Boom()
        let model = await modelAtPace(harness)
        model.next()
        await model.work?.value
        #expect(model.step.id == .pace)
        #expect(model.completeFailed)
        #expect(!model.isDone)
        #expect(!model.isCompleting)
        #expect(model.doneNote == nil)
        #expect(model.name == "Maya")

        harness.service.completeFailure = nil
        model.next()
        await model.work?.value
        #expect(harness.service.completeCalls.count == 1)
        #expect(harness.service.completeCalls.first?.displayName == "Maya")
        #expect(!model.completeFailed)
        #expect(model.isDone)
    }

    @Test("Continue with no pace sends with no pace, and Skip clears a chosen one")
    func noPaceAndSkip() async {
        let harness = OnboardingFlowHarness()
        let model = await modelAtPace(harness)
        model.next()
        await model.work?.value
        #expect(harness.service.completeCalls.count == 1)
        #expect(harness.service.completeCalls.first?.pace == nil)

        let other = OnboardingFlowHarness()
        let second = await modelAtPace(other)
        second.selectPace(.fewAWeek)
        second.skip()
        await second.work?.value
        #expect(second.pace == nil)
        #expect(other.service.completeCalls.first?.pace == nil)
    }

    @Test("a person is never sent without a glyph, and the glyphs get one reload first")
    func neverWithoutGlyph() async {
        let harness = OnboardingFlowHarness()
        let model = await modelAtPace(harness, withPerson: true)
        // The glyphs vanish after the person was named, as a failed first read leaves them.
        model.seed(step: .pace, letter: nil, systemGlyphs: [], consent: model.consent, people: model.people)
        harness.service.glyphs = []
        let before = harness.service.glyphCalls
        model.next()
        await model.work?.value
        #expect(harness.service.completeCalls.isEmpty)
        #expect(model.completeFailed)
        #expect(model.step.id == .pace)
        #expect(harness.service.glyphCalls == before + 1)

        // The reload lands: the retry sends the named person.
        harness.service.glyphs = OnboardingFlowHarness.glyphs
        model.next()
        await model.work?.value
        #expect(harness.service.completeCalls.first?.souls.count == 1)
        #expect(model.isDone)
    }

    @Test("a double tap sends once, and the chrome holds while it is in flight")
    func doubleTapSendsOnce() async {
        let harness = OnboardingFlowHarness()
        let reached = Latch()
        let release = Latch()
        harness.service.onComplete = {
            reached.open()
            await release.wait()
        }
        let model = await modelAtPace(harness)
        model.next()
        #expect(await reached.reached())
        #expect(model.isCompleting)
        #expect(model.chromeDisabled)
        model.next()
        model.skip()
        model.back()
        #expect(model.step.id == .pace)
        release.open()
        await model.work?.value
        #expect(harness.service.completeCalls.count == 1)
        #expect(model.isDone)
        #expect(!model.chromeDisabled)
    }

    @Test("pace is not changed while a send is in flight")
    func paceHeldWhileSending() async {
        let harness = OnboardingFlowHarness()
        let reached = Latch()
        let release = Latch()
        harness.service.onComplete = {
            reached.open()
            await release.wait()
        }
        let model = await modelAtPace(harness)
        model.selectPace(.daily)
        model.next()
        #expect(await reached.reached())
        model.selectPace(.weeklyWrap)
        #expect(model.pace == .daily)
        release.open()
        await model.work?.value
    }

    @Test("no write starts after the session ended")
    func noWriteAfterSessionEnded() async {
        let harness = OnboardingFlowHarness()
        let model = await modelAtPace(harness)
        model.sessionEnded()
        model.next()
        await model.work?.value
        #expect(harness.service.completeCalls.isEmpty)
        #expect(!model.isDone)
    }

    @Test("a session that ends during the send takes no state back")
    func sessionEndsDuringSend() async {
        let harness = OnboardingFlowHarness()
        let reached = Latch()
        let release = Latch()
        harness.service.onComplete = {
            reached.open()
            await release.wait()
        }
        let model = await modelAtPace(harness)
        model.next()
        #expect(await reached.reached())
        model.sessionEnded()
        release.open()
        await model.work?.value
        #expect(model.doneNote == nil)
        #expect(!model.isDone)
    }
}
