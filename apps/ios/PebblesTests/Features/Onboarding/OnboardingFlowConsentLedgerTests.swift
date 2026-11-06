import Foundation
import Testing
@testable import Pebbles

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

    @Test("the error line does not outlive its failure: a read covers the remainder and Continue moves on")
    func failedClearsWhenNothingIsLeftToRecord() async throws {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        await model.loadToConsent()
        model.switchEveryActOn()
        harness.consents.failingKinds = [.terms]
        model.next()
        await model.work?.value
        #expect(model.consent.failed)

        // The remainder lands from another surface, and a later read shows it.
        harness.consents.failingKinds = []
        try await harness.consents.record(
            kind: .terms,
            documentVersion: ConsentGateLogic.version(for: .terms),
            source: "web_settings"
        )
        await model.load()
        #expect(model.consent.toRecord.isEmpty)

        model.next()
        #expect(model.step.id == .reasons)
        #expect(!model.consent.failed)
        model.back()
        #expect(model.step.id == .consent)
        #expect(!model.consent.failed, "no error under four locked acts")
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

    /// The one act left is also the last, so the loop has no next turn to stop on and
    /// only `finishRecording`'s own check stands between it and the cache.
    @Test("a session that ends during the last act marks nothing and moves nowhere")
    func sessionEndsDuringTheLastAct() async {
        let harness = OnboardingFlowHarness(rows: [
            OnboardingFlowHarness.row(.healthData),
            OnboardingFlowHarness.row(.terms),
            OnboardingFlowHarness.row(.privacy)
        ])
        let model = harness.model()
        await model.loadToConsent()
        model.toggleConsent(.ageAssurance, true)
        let reached = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            reached.open()
            await release.wait()
        }
        model.next()
        #expect(await reached.reached())
        let running = model.work

        model.sessionEnded()
        harness.forgetTaps()
        release.open()
        await running?.value

        #expect(harness.consents.recordCalls.map(\.kind) == [.ageAssurance])
        #expect(!harness.gateIsSatisfied)
        #expect(model.step.id == .consent)
        #expect(harness.taps.isEmpty, "no .advance")
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
