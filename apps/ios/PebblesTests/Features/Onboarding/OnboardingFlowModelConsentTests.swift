import Foundation
import Testing
@testable import Pebbles

/// Step 2: Android's consent cases, then Continue. Nothing reaches the ledger
/// before Continue, and what is on record stays locked.
@Suite("OnboardingFlowModel consent")
@MainActor
struct OnboardingFlowModelConsentTests {
    private struct Boom: Error {}

    // MARK: Android's cases

    @Test("consent asks only the missing acts and records them on Continue")
    func asksOnlyMissing() async {
        let harness = OnboardingFlowHarness(rows: [OnboardingFlowHarness.row(.terms)])
        let model = harness.model()
        await model.loadToConsent()
        #expect(model.consent.recorded == [.terms])
        #expect(!model.consent.canContinue)

        [ConsentKind.healthData, .privacy, .ageAssurance].forEach { model.toggleConsent($0, true) }
        #expect(model.consent.canContinue)
        #expect(harness.consents.recordCalls.isEmpty, "a switch alone writes nothing")

        model.next()
        await model.work?.value

        #expect(harness.consents.recordCalls.map(\.kind) == [.healthData, .privacy, .ageAssurance])
        #expect(model.step.id == .reasons)
        #expect(harness.gateIsSatisfied)
    }

    @Test("a switch turns back off, and an act on record stays on")
    func switchesAndLocks() async {
        let harness = OnboardingFlowHarness(rows: [OnboardingFlowHarness.row(.terms)])
        let model = harness.model()
        await model.loadToConsent()
        model.toggleConsent(.healthData, true)
        #expect(model.consent.on == [.healthData])
        model.toggleConsent(.healthData, false)
        model.toggleConsent(.terms, false)
        #expect(!model.consent.on.contains(.healthData))
        #expect(model.consent.recorded.contains(.terms))
        #expect(harness.consents.recordCalls.isEmpty)
    }

    @Test("a failed record stays on the step, and the retry goes through")
    func failedRecord() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        harness.consents.recordFailure = URLError(.notConnectedToInternet)
        model.next()
        await model.work?.value
        #expect(model.step.id == .consent)
        #expect(model.consent.failed)
        #expect(!model.consent.isSaving)
        #expect(model.consent.recorded.isEmpty)

        harness.consents.recordFailure = nil
        model.next()
        #expect(!model.consent.failed, "the retry clears the line while it runs")
        await model.work?.value
        #expect(model.step.id == .reasons)
        #expect(!model.consent.failed)
    }

    // MARK: Continue

    @Test("Continue does nothing until all four are on or recorded")
    func continueNeedsAllFour() async {
        let harness = OnboardingFlowHarness(rows: [OnboardingFlowHarness.row(.terms)])
        let model = harness.model()
        await model.loadToConsent()
        model.toggleConsent(.healthData, true)
        model.toggleConsent(.privacy, true)
        model.next()
        await model.work?.value
        #expect(model.step.id == .consent)
        #expect(!model.consent.isSaving)
        #expect(harness.consents.recordCalls.isEmpty)
    }

    @Test("with nothing to record, Continue moves on without a write")
    func nothingToRecord() async {
        let harness = OnboardingFlowHarness(rows: OnboardingConsentState.order.map { OnboardingFlowHarness.row($0) })
        let model = harness.model()
        await model.loadToConsent()
        #expect(model.consent.recorded == Set(OnboardingConsentState.order))
        #expect(model.consent.canContinue)
        #expect(!model.consent.hasMissingActs)
        model.next()
        #expect(model.step.id == .reasons)
        #expect(model.work == nil)
        #expect(harness.consents.recordCalls.isEmpty)
        #expect(!harness.gateIsSatisfied, "this step marks the cache only after a write")

        let gate = ConsentGateModel()
        gate.service = harness.consents
        gate.cache = harness.cache
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.isPassed)
        #expect(harness.gateIsSatisfied, "the gate's own check marks it")
    }

    @Test("every act goes in display order, at the gate's version and source")
    func recordsWhatTheGateWould() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadPastConsent()
        let calls = harness.consents.recordCalls
        #expect(calls.map(\.kind) == [.healthData, .terms, .privacy, .ageAssurance])
        #expect(calls.allSatisfy { $0.source == ConsentGateLogic.source })
        #expect(calls.allSatisfy { $0.source == "ios_settings" })
        #expect(calls.allSatisfy { $0.version == ConsentGateLogic.version(for: $0.kind) })
    }

    @Test("while saving: the flag is up, the chrome holds, switches and a second Continue are ignored")
    func whileSaving() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        let reached = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            reached.open()
            await release.wait()
        }
        model.switchEveryActOn()
        model.next()
        #expect(model.consent.isSaving)
        #expect(model.chromeDisabled)
        #expect(!model.consent.canContinue)
        #expect(await reached.reached())
        let running = model.work

        model.toggleConsent(.terms, false)
        #expect(model.consent.on.contains(.terms))
        model.next()
        model.back()
        model.skip()
        #expect(model.step.id == .consent)
        #expect(model.work == running)

        release.open()
        await running?.value
        #expect(!model.consent.isSaving)
        #expect(!model.chromeDisabled)
        #expect(model.step.id == .reasons)
        #expect(harness.consents.recordCalls.count == 4)
    }

    @Test("the flow's four are exactly the gate's four")
    func sameActsAsTheGate() {
        #expect(Set(OnboardingConsentState.order) == Set(ConsentGateLogic.required))
        #expect(OnboardingConsentState.order == [.healthData, .terms, .privacy, .ageAssurance])
    }

    // MARK: Haptics

    @Test("a switch is a selection, a refused one is silent, the write is felt when it answers")
    func haptics() async {
        let harness = OnboardingFlowHarness(rows: [OnboardingFlowHarness.row(.terms)])
        let model = harness.model()
        await model.loadToConsent()
        harness.forgetTaps()

        model.toggleConsent(.terms, false) // locked
        #expect(harness.taps.isEmpty)
        model.toggleConsent(.healthData, true)
        #expect(harness.taps == [.selection])

        model.next() // blocked: two are still off
        #expect(harness.taps == [.selection, .warning])

        [ConsentKind.privacy, .ageAssurance].forEach { model.toggleConsent($0, true) }
        harness.forgetTaps()
        harness.consents.recordFailure = Boom()
        model.next()
        model.next() // a second tap while saving
        #expect(harness.taps.isEmpty, "the answer is what is felt, not the tap")
        await model.work?.value
        #expect(harness.taps == [.warning])

        harness.forgetTaps()
        harness.consents.recordFailure = nil
        model.next()
        await model.work?.value
        #expect(harness.taps == [.advance])
    }
}
