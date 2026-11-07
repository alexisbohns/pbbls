import Foundation
import Testing
@testable import Pebbles

/// Android's `ConsentGateViewModelTest`, the gate cases ported one to one.
/// The deletion-from-the-gate cases (#1030) are not ported: iOS has no
/// account-deletion flow outside Settings yet.
@Suite("Consent gate model")
@MainActor
struct ConsentGateModelTests {
    let cache: ConsentPreferences

    init() {
        let suite = "consent-gate-tests-\(UUID().uuidString)"
        cache = ConsentPreferences(defaults: UserDefaults(suiteName: suite)!)
    }

    func model(_ consents: FakeConsentService = FakeConsentService()) -> ConsentGateModel {
        let model = ConsentGateModel()
        model.service = consents
        model.cache = cache
        return model
    }

    func form(_ model: ConsentGateModel) -> ConsentGateModel.Required? {
        if case .required(let form) = model.state { return form }
        return nil
    }

    @Test("no user is idle and never checks")
    func noUser() async {
        let consents = FakeConsentService()
        let gate = model(consents)
        gate.start(userId: nil)
        await gate.work?.value
        #expect(gate.state == .idle)
        #expect(consents.activeCalls == 0)
        #expect(!gate.isPassed)
    }

    @Test("a satisfied ledger passes and is cached")
    func satisfiedPasses() async {
        let gate = model()
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(gate.isPassed)
        #expect(cache.isSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint()))
    }

    @Test("a cached pass skips the network")
    func cachedPass() async {
        cache.markSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint())
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(consents.activeCalls == 0)
    }

    @Test("an empty ledger asks for all four")
    func emptyLedger() async {
        let gate = model(FakeConsentService(rows: []))
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(form(gate)?.missing == ConsentGateLogic.required)
        #expect(form(gate)?.canContinue == false)
        #expect(!gate.isPassed)
    }

    @Test("continue records each missing act and passes")
    func continueRecords() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        ConsentGateLogic.required.forEach { gate.toggle($0, true) }

        gate.submit()
        await gate.work?.value

        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(consents.recordCalls.map(\.kind) == ConsentGateLogic.required)
        #expect(consents.recordCalls.allSatisfy { $0.source == "ios_settings" })
        #expect(consents.recordCalls.first { $0.kind == .terms }?.version == "1.2.0")
        #expect(consents.recordCalls.first { $0.kind == .healthData }?.version == "1.5.0")
    }

    @Test("continue does nothing until every shown box is ticked")
    func continueNeedsEveryBox() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        gate.toggle(.terms, true)
        gate.submit()
        await gate.work?.value
        #expect(consents.recordCalls.isEmpty)
        #expect(form(gate)?.isSubmitting == false)
    }

    @Test("only the outdated act is asked for")
    func onlyOutdated() async {
        let rows = FakeConsentService.satisfied().map {
            $0.kind == "terms" ? ActiveConsent(kind: "terms", documentVersion: "1.0.0") : $0
        }
        let gate = model(FakeConsentService(rows: rows))
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(form(gate)?.missing == [.terms])
    }

    @Test("a failed check fails closed, and retry recovers")
    func failedCheck() async {
        let consents = FakeConsentService()
        consents.activeFailure = URLError(.notConnectedToInternet)
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.state == .failed(.offline))
        #expect(!gate.isPassed)

        consents.activeFailure = nil
        gate.retry()
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
    }

    @Test("a failed record keeps the form and shows an error")
    func failedRecord() async {
        let consents = FakeConsentService(rows: [])
        consents.recordFailure = URLError(.timedOut)
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        ConsentGateLogic.required.forEach { gate.toggle($0, true) }
        gate.submit()
        await gate.work?.value
        let state = form(gate)
        #expect(state?.error == .offline)
        #expect(state?.isSubmitting == false)
        #expect(state?.ticked == Set(ConsentGateLogic.required))
    }

    @Test("a new user re-checks")
    func newUser() async {
        let consents = FakeConsentService()
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        gate.start(userId: "user-2")
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-2"))
        #expect(consents.activeCalls == 2)
    }

    // MARK: - iOS-only

    @Test("a check for the same user is not restarted")
    func idempotent() async {
        let consents = FakeConsentService()
        let gate = model(consents)
        gate.start(userId: "user-1")
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(consents.activeCalls == 1)
    }

    @Test("signing out closes the gate's pass")
    func signOut() async {
        let gate = model()
        gate.start(userId: "user-1")
        await gate.work?.value
        gate.start(userId: nil)
        #expect(gate.state == .idle)
        #expect(!gate.isPassed)
    }

    @Test("a server error is not reported as offline")
    func otherError() async {
        struct Boom: Error {}
        let consents = FakeConsentService()
        consents.activeFailure = Boom()
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.state == .failed(.other))
    }

    @Test("without a service the gate fails closed")
    func noService() {
        let gate = ConsentGateModel()
        gate.cache = cache
        gate.start(userId: "user-1")
        #expect(gate.state == .failed(.other))
        #expect(!gate.isPassed)
    }

    /// Holds the first record until opened.
    actor Latch {
        private(set) var entered = false
        private var continuation: CheckedContinuation<Void, Never>?

        func hold() async {
            guard !entered else { return }
            entered = true
            await withCheckedContinuation { continuation = $0 }
        }

        func open() {
            continuation?.resume()
            continuation = nil
        }
    }

    @Test("a record in flight for one user never continues under the next")
    func userSwitchCancelsRecords() async {
        let consents = FakeConsentService(rows: [])
        let latch = Latch()
        consents.beforeRecord = { await latch.hold() }
        let gate = model(consents)
        gate.start(userId: "user-a")
        await gate.work?.value
        ConsentGateLogic.required.forEach { gate.toggle($0, true) }

        gate.submit()
        let aWork = gate.work
        while await !latch.entered { await Task.yield() }

        gate.start(userId: "user-b")
        await gate.work?.value
        await latch.open()
        await aWork?.value

        // Only A's first act, already in flight at the switch, reached the
        // ledger; nothing after it did.
        #expect(consents.recordCalls.map(\.kind) == [.terms])
        #expect(form(gate)?.missing == ConsentGateLogic.required)
        #expect(form(gate)?.isSubmitting == false)
        #expect(!gate.isPassed)
    }
}
