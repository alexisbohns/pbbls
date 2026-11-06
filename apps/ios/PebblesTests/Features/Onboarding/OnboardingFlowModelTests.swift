import Foundation
import Testing
@testable import Pebbles

/// Android's `OnboardingViewModelTest`, the cases for loading and reasons.
/// Stepping is below, and the consent cases are in `OnboardingFlowModelConsentTests.swift`.
@Suite("OnboardingFlowModel")
@MainActor
struct OnboardingFlowModelTests {
    private struct Boom: Error {}

    // MARK: Loading

    @Test("starts on the promise with the letter")
    func startsOnThePromise() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        #expect(model.step.id == .promise)
        #expect(model.stepIndex == 0)
        #expect(model.letter?.bodyEn == "There will never be a feed here.")
        #expect(!model.letterUnavailable)
        #expect(model.systemGlyphs.count == 8)
    }

    @Test("a letter that fails to load still lets the flow start")
    func letterFails() async {
        let harness = OnboardingFlowHarness()
        harness.service.letterFailure = Boom()
        let model = harness.model()
        await model.load()
        #expect(model.letter == nil)
        #expect(model.letterUnavailable)
        #expect(model.systemGlyphs.count == 8)
        model.next()
        #expect(model.step.id == .consent)
    }

    @Test("no published letter reads as unavailable too")
    func noLetter() async {
        let harness = OnboardingFlowHarness()
        harness.service.letter = nil
        let model = harness.model()
        await model.load()
        #expect(model.letterUnavailable)
    }

    @Test("a load the view cancelled changes nothing")
    func cancelledLoad() async {
        let harness = OnboardingFlowHarness()
        harness.service.letterFailure = URLError(.cancelled)
        let model = harness.model()
        let task = Task { await model.load() }
        task.cancel()
        await task.value
        #expect(!model.letterUnavailable)
    }

    @Test("a held ledger read holds up neither the letter nor the glyphs")
    func heldLedgerReadHoldsNothingElse() async {
        let harness = OnboardingFlowHarness()
        let reached = Latch()
        let release = Latch()
        harness.consents.afterActiveRead = {
            reached.open()
            await release.wait()
        }
        let model = harness.model()
        let loading = Task { await model.load() }
        #expect(await reached.reached())

        let deadline = ContinuousClock.now + .seconds(10)
        while model.letter == nil || model.systemGlyphs.isEmpty, ContinuousClock.now < deadline {
            try? await Task.sleep(for: .milliseconds(5))
        }
        #expect(model.letter == OnboardingFlowHarness.letter)
        #expect(model.systemGlyphs.count == 8)

        release.open()
        await loading.value
    }

    @Test("a failed reload keeps the letter on screen")
    func failedReloadKeepsTheLetter() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        harness.service.letterFailure = Boom()
        await model.load()
        #expect(model.letter == OnboardingFlowHarness.letter)
        #expect(!model.letterUnavailable)
    }

    // MARK: Glyph retries

    @Test("an empty glyph read is retried on the step that shows them")
    func emptyGlyphsRetried() async {
        let harness = OnboardingFlowHarness()
        harness.service.glyphs = []
        let model = harness.model()
        await model.loadPastConsent()
        #expect(model.systemGlyphs.isEmpty)
        #expect(harness.service.glyphCalls == 1)
        harness.service.glyphs = OnboardingFlowHarness.glyphs
        model.skip() // you
        await model.glyphWork?.value
        #expect(model.step.id == .you)
        #expect(model.systemGlyphs.count == 8)
        #expect(harness.service.glyphCalls == 2)
    }

    @Test("people retries too, and a failed read is retried like an empty one")
    func failedGlyphsRetriedOnPeople() async {
        let harness = OnboardingFlowHarness()
        harness.service.glyphsFailure = Boom()
        let model = harness.model()
        await model.loadPastConsent()
        model.skip() // you, the retry fails too
        await model.glyphWork?.value
        #expect(model.systemGlyphs.isEmpty)
        harness.service.glyphsFailure = nil
        model.skip() // people
        await model.glyphWork?.value
        #expect(model.step.id == .people)
        #expect(model.systemGlyphs.count == 8)
    }

    @Test("glyphs in hand are not read again, and only those two steps retry")
    func noNeedlessRetry() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadPastConsent()
        model.skip() // you
        await model.glyphWork?.value
        #expect(harness.service.glyphCalls == 1)

        let empty = OnboardingFlowHarness()
        empty.service.glyphs = []
        let bare = empty.model()
        await bare.loadPastConsent() // consent and reasons do not show them
        #expect(empty.service.glyphCalls == 1)
    }

    @Test("an empty read never takes back the glyphs already shown")
    func emptyReadKeepsGlyphs() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        harness.service.glyphs = []
        await model.load()
        #expect(model.systemGlyphs.count == 8)
    }

    // MARK: Reasons and answers

    @Test("reasons keep pick order, and a second tap takes one back")
    func reasonsKeepPickOrder() {
        let model = OnboardingFlowHarness().model()
        model.toggleReason(.keepPeople)
        model.toggleReason(.noticeFeelings)
        #expect(model.reasons == [.keepPeople, .noticeFeelings])
        #expect(model.answers.reasons == [.keepPeople, .noticeFeelings])
        model.toggleReason(.keepPeople)
        #expect(model.reasons == [.noticeFeelings])
    }

    @Test("answers: no reasons is nil, the name is trimmed and blank is nil")
    func answers() {
        let model = OnboardingFlowHarness().model()
        #expect(model.answers.reasons == nil)
        #expect(model.answers.displayName == nil)
        model.name = "  \n "
        #expect(model.answers.displayName == nil)
        model.name = "  Maya "
        #expect(model.answers.displayName == "Maya")
    }

    @Test("seed lands on the step it names, and the index counts the visible steps")
    func seeding() {
        let model = OnboardingFlowHarness().model()
        var consent = OnboardingConsentState()
        consent.recorded = [.terms]
        model.seed(
            step: .reasons,
            letter: OnboardingFlowHarness.letter,
            systemGlyphs: OnboardingFlowHarness.glyphs,
            consent: consent
        )
        #expect(model.step.id == .reasons)
        #expect(model.stepIndex == 2)
        #expect(model.letter == OnboardingFlowHarness.letter)
        #expect(model.systemGlyphs.count == 8)
        #expect(model.consent == consent)
    }
}

@Suite("OnboardingFlowModel stepping")
@MainActor
struct OnboardingFlowSteppingTests {
    private func seeded(
        _ id: OnboardingFlowStep.StepID,
        glyphId: UUID? = nil,
        people: [OnboardingPerson] = [],
        pace: OnboardingPace? = nil
    ) -> OnboardingFlowModel {
        let model = OnboardingFlowHarness().model()
        model.seed(
            step: id,
            letter: nil,
            systemGlyphs: OnboardingFlowHarness.glyphs,
            consent: OnboardingConsentState(),
            glyphId: glyphId,
            people: people,
            pace: pace
        )
        return model
    }

    @Test("the steps run in order, and pace stops there until completion sends")
    func walk() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        var seen = [model.stepIndex]
        model.next() // consent
        seen.append(model.stepIndex)
        model.switchEveryActOn()
        model.next()
        await model.work?.value // reasons
        seen.append(model.stepIndex)
        for _ in 0..<3 {
            model.next()
            seen.append(model.stepIndex)
        }
        #expect(seen == [0, 1, 2, 3, 4, 5])
        #expect(model.step.id == .pace)
        harness.forgetTaps()
        model.next()
        #expect(model.step.id == .pace)
        #expect(harness.taps.isEmpty)
    }

    @Test("back from the promise does nothing and consent cannot be skipped")
    func backAndSkipGuards() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        model.back()
        #expect(model.step.id == .promise)
        model.skip()
        #expect(model.step.id == .promise)
        model.next()
        model.skip()
        #expect(model.step.id == .consent)
    }

    @Test("skip leaves the answer null and back returns")
    func skipThenBack() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadPastConsent()
        model.toggleReason(.keepPeople)
        model.skip() // reasons → you, reasons dropped
        #expect(model.step.id == .you)
        #expect(model.answers.reasons == nil)
        model.back()
        #expect(model.step.id == .reasons)
        #expect(model.reasons.isEmpty)
    }

    @Test("skip on you clears the name and the glyph and moves on")
    func skipYou() {
        let model = seeded(.you, glyphId: OnboardingFlowHarness.glyphs[2].id)
        model.name = "Maya"
        model.skip()
        #expect(model.step.id == .people)
        #expect(model.name.isEmpty)
        #expect(model.glyphId == nil)
    }

    @Test("Later on people clears them and moves on")
    func skipPeople() {
        let model = seeded(.people, people: [OnboardingPerson(name: "Noé", isEditing: false)])
        model.skip()
        #expect(model.step.id == .pace)
        #expect(model.people.isEmpty)
    }

    @Test("skip on pace clears the pace")
    func skipPace() {
        let model = seeded(.pace, pace: .daily)
        model.skip()
        #expect(model.step.id == .pace)
        #expect(model.pace == nil)
    }

    @Test("a move clears the satellites and a refused completion")
    func moveClears() {
        let model = OnboardingFlowHarness().model()
        model.seed(
            step: .people,
            letter: nil,
            systemGlyphs: OnboardingFlowHarness.glyphs,
            consent: OnboardingConsentState(),
            glyphSheetFor: 1,
            carvingFor: .person(1),
            completeFailed: true
        )
        model.next()
        #expect(model.step.id == .pace)
        #expect(model.glyphSheetFor == nil)
        #expect(model.carvingFor == nil)
        #expect(!model.completeFailed)
    }

    @Test("the chrome is disabled while consent is saving or the flow is completing")
    func chromeDisabled() {
        let model = OnboardingFlowHarness().model()
        #expect(!model.chromeDisabled)
        var saving = OnboardingConsentState()
        saving.isSaving = true
        model.seed(step: .consent, letter: nil, systemGlyphs: [], consent: saving)
        #expect(model.chromeDisabled)
        model.seed(step: .pace, letter: nil, systemGlyphs: [], consent: OnboardingConsentState(), isCompleting: true)
        #expect(model.chromeDisabled)
    }

    @Test("while the chrome holds, Back does not move and Skip neither moves nor clears the answer")
    func chromeHolds() {
        let model = OnboardingFlowHarness().model()
        model.seed(
            step: .pace,
            letter: nil,
            systemGlyphs: [],
            consent: OnboardingConsentState(),
            pace: .daily,
            isCompleting: true
        )
        model.back()
        #expect(model.step.id == .pace)
        model.skip()
        #expect(model.pace == .daily)

        model.seed(step: .reasons, letter: nil, systemGlyphs: [], consent: OnboardingConsentState(), isCompleting: true)
        model.skip()
        #expect(model.step.id == .reasons)
    }

    @Test("a real step and a real pick are felt, a refused one is not")
    func haptics() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.load()
        model.back() // first step: nothing to go back to
        model.skip() // promise cannot be skipped
        #expect(harness.taps.isEmpty)
        model.next()
        #expect(harness.taps == [.advance])
        model.back()
        #expect(harness.taps == [.advance, .advance])

        harness.forgetTaps()
        model.seedStep(.reasons)
        model.toggleReason(.goodDays)
        model.skip()
        #expect(harness.taps == [.selection, .advance])
    }
}
