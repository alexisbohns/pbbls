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

/// Step 2 against the ledger's rules: a partial failure keeps what landed, the
/// gate's cache waits for all four, a late read takes nothing back, and no act
/// goes out for anyone else.
@Suite("OnboardingFlowModel ledger")
@MainActor
struct OnboardingFlowConsentLedgerTests {
    private struct Boom: Error {}

    // MARK: Partial failure and the cache

    @Test("a partial failure keeps what landed, and the retry sends only the remainder")
    func partialFailure() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        harness.consents.failingKinds = [.terms]
        model.next()
        await model.work?.value

        #expect(model.step.id == .consent)
        #expect(model.consent.failed)
        #expect(
            model.consent.recorded == [.healthData, .privacy, .ageAssurance],
            "the acts after the failure are still tried"
        )
        #expect(model.consent.toRecord == [.terms])
        #expect(!harness.gateIsSatisfied)

        harness.consents.failingKinds = []
        model.next()
        await model.work?.value

        #expect(harness.consents.recordCalls.map(\.kind) == [.healthData, .privacy, .ageAssurance, .terms])
        #expect(model.step.id == .reasons)
        #expect(!model.consent.failed)
        #expect(harness.gateIsSatisfied)
    }

    @Test("an act that landed cannot be switched off while a retry is owed")
    func landedActLocks() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        harness.consents.failingKinds = [.terms]
        model.next()
        await model.work?.value
        model.toggleConsent(.healthData, false)
        #expect(model.consent.on.contains(.healthData))
        model.toggleConsent(.terms, false)
        #expect(!model.consent.on.contains(.terms))
        #expect(!model.consent.failed, "touching a switch clears the line")
    }

    @Test("the gate's cache stays cold until all four are on record")
    func cacheWaitsForAllFour() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        harness.consents.recordFailure = Boom()
        model.next()
        await model.work?.value
        #expect(!harness.gateIsSatisfied)
    }

    @Test("without a user id the flow moves on and marks nothing")
    func noUserId() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model(userId: nil)
        await model.loadPastConsent()
        #expect(model.step.id == .reasons)
        #expect(harness.cache.defaults.string(forKey: ConsentPreferences.key) == nil)
    }

    // MARK: The ledger read

    @Test("an older row is asked again, a newer one is on record")
    func ledgerVersions() async {
        let harness = OnboardingFlowHarness(rows: [
            OnboardingFlowHarness.row(.terms, version: "0.0.1"),
            OnboardingFlowHarness.row(.privacy, version: "99.0.0")
        ])
        let model = harness.model()
        await model.load()
        #expect(model.consent.recorded == [.privacy])
    }

    @Test("a failed ledger read leaves every act asked, and the flow still passes")
    func ledgerReadFails() async {
        let harness = OnboardingFlowHarness(rows: [OnboardingFlowHarness.row(.terms)])
        harness.consents.activeFailure = Boom()
        let model = harness.model()
        await model.loadToConsent()
        #expect(model.consent.recorded.isEmpty)
        #expect(model.consent.hasMissingActs)
        model.switchEveryActOn()
        model.next()
        await model.work?.value
        #expect(model.step.id == .reasons)
        #expect(harness.consents.recordCalls.count == 4)
    }

    @Test("a ledger read that lands late never takes back an act recorded here")
    func lateLedgerRead() async {
        let harness = OnboardingFlowHarness()
        let reached = Latch()
        let release = Latch()
        harness.consents.afterActiveRead = {
            reached.open()
            await release.wait()
        }
        let model = harness.model()
        let loading = Task { await model.load() }
        #expect(await reached.reached(), "the read has seen an empty ledger and not answered yet")
        model.next()
        model.switchEveryActOn()
        model.next()
        await model.work?.value
        #expect(model.consent.recorded == Set(OnboardingConsentState.order))

        release.open()
        await loading.value
        #expect(model.consent.recorded == Set(OnboardingConsentState.order))
        #expect(!model.consent.hasMissingActs)
    }

    // MARK: The session

    /// Holds the first act in flight, and returns once it has been sent.
    private func holdFirstAct(_ harness: OnboardingFlowHarness, _ model: OnboardingFlowModel) async -> Latch {
        await model.loadToConsent()
        model.switchEveryActOn()
        let reached = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            reached.open()
            await release.wait()
        }
        model.next()
        #expect(await reached.reached())
        return release
    }

    @Test("a session that ends mid-write sends nothing after the act in flight")
    func sessionEndsMidWrite() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        let release = await holdFirstAct(harness, model)
        let running = model.work

        model.sessionEnded()
        #expect(!model.consent.isSaving)
        release.open()
        await running?.value

        #expect(harness.consents.recordCalls.map(\.kind) == [.healthData])
        #expect(!harness.gateIsSatisfied)
        #expect(model.step.id == .consent)
        #expect(!model.consent.failed)
    }

    @Test("after the session ended, Continue sends nothing at all")
    func nothingAfterSessionEnded() async {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        model.sessionEnded()
        model.next()
        await model.work?.value
        #expect(harness.consents.recordCalls.isEmpty)
        #expect(!model.consent.isSaving)
        #expect(model.step.id == .consent)
    }

    @Test("a model dropped mid-write sends nothing after the act in flight")
    func droppedModelStops() async {
        let harness = OnboardingFlowHarness()
        var model: OnboardingFlowModel? = harness.model()
        let release = await holdFirstAct(harness, model!)
        let running = model?.work

        model = nil
        release.open()
        await running?.value

        #expect(harness.consents.recordCalls.map(\.kind) == [.healthData])
        #expect(!harness.gateIsSatisfied)
    }
}
