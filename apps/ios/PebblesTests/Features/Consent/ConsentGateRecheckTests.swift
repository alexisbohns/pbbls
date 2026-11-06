import Foundation
import Testing
@testable import Pebbles

/// The gate's side of the onboarding's end: `recheck` (Android's `restart`), and a pass staying bound
/// to the user it was given to.
@Suite("Consent gate recheck")
@MainActor
struct ConsentGateRecheckTests {
    let cache: ConsentPreferences

    init() throws {
        let suite = "consent-gate-recheck-\(UUID().uuidString)"
        cache = ConsentPreferences(defaults: try #require(UserDefaults(suiteName: suite)))
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

    @Test("a pass opens the app only for the user it was given to")
    func passIsBoundToItsUser() async {
        let gate = model()
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.isPassed(for: "user-1"))
        // The session moved, and `start` has not heard yet.
        #expect(!gate.isPassed(for: "user-2"))
        #expect(!gate.isPassed(for: nil))
    }

    // MARK: - recheck (Android's `restart`)

    @Test("recheck for no user does nothing")
    func recheckNoUser() async {
        let consents = FakeConsentService()
        let gate = model(consents)
        gate.recheck()
        await gate.work?.value
        #expect(gate.state == .idle)
        #expect(consents.activeCalls == 0)
        #expect(!gate.isPassed)
    }

    @Test("recheck with the cache satisfied passes at once, without the network")
    func recheckCached() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(form(gate) != nil)
        let reads = consents.activeCalls

        cache.markSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint())
        gate.recheck()

        // In the same turn: nothing has run between the call and this line.
        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(gate.isPassed)
        await gate.work?.value
        #expect(consents.activeCalls == reads)
    }

    @Test("recheck with no cached pass reads the ledger, and a full one passes and is cached")
    func recheckChecksTheLedger() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        for kind in ConsentGateLogic.required {
            try? await consents.record(kind: kind, documentVersion: ConsentGateLogic.version(for: kind), source: "t")
        }

        gate.recheck()
        #expect(gate.state == .checking)
        await gate.work?.value

        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(cache.isSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint()))
    }

    @Test("recheck with an act still missing asks for it")
    func recheckStillMissing() async {
        let gate = model(FakeConsentService(rows: []))
        gate.start(userId: "user-1")
        await gate.work?.value
        gate.recheck()
        await gate.work?.value
        #expect(form(gate)?.missing == ConsentGateLogic.required)
        #expect(!gate.isPassed)
    }

    @Test("recheck with an unreadable ledger fails closed")
    func recheckUnreadable() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        gate.start(userId: "user-1")
        await gate.work?.value
        consents.activeFailure = URLError(.notConnectedToInternet)
        gate.recheck()
        await gate.work?.value
        #expect(gate.state == .failed(.offline))
        #expect(!gate.isPassed)
    }

    @Test("recheck never carries one user's cached pass to another")
    func recheckOtherUser() async {
        let consents = FakeConsentService(rows: [])
        let gate = model(consents)
        cache.markSatisfied(userId: "user-other", fingerprint: ConsentGateLogic.fingerprint())
        gate.start(userId: "user-1")
        await gate.work?.value
        gate.recheck()
        await gate.work?.value
        #expect(!gate.isPassed)
    }

    /// The gate's first read can still be out when the flow ends, holding a ledger from before step 2.
    @Test("a check still in flight does not take back a recheck's pass")
    func staleCheckAfterRecheck() async {
        let consents = FakeConsentService(rows: [])
        let entered = Latch()
        let release = Latch()
        consents.afterActiveRead = {
            entered.open()
            await release.wait()
        }
        let gate = model(consents)
        gate.start(userId: "user-1")
        let firstCheck = gate.work
        #expect(await entered.reached())

        cache.markSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint())
        gate.recheck()
        #expect(gate.isPassed)

        release.open()
        await firstCheck?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
    }

    @Test("a check failing in flight does not take back a recheck's pass")
    func staleFailureAfterRecheck() async {
        let consents = FakeConsentService(rows: [])
        let entered = Latch()
        let release = Latch()
        consents.beforeActiveRead = {
            entered.open()
            await release.wait()
        }
        let gate = model(consents)
        gate.start(userId: "user-1")
        let firstCheck = gate.work
        #expect(await entered.reached())

        cache.markSatisfied(userId: "user-1", fingerprint: ConsentGateLogic.fingerprint())
        gate.recheck()
        consents.activeFailure = URLError(.cancelled)

        release.open()
        await firstCheck?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
    }

    @Test("a newer check's answer is the one that lands")
    func olderCheckDoesNotLandOverNewer() async {
        let consents = FakeConsentService(rows: [])
        let entered = Latch()
        let release = Latch()
        consents.afterActiveRead = {
            entered.open()
            await release.wait()
        }
        let gate = model(consents)
        gate.start(userId: "user-1")
        let firstCheck = gate.work
        #expect(await entered.reached())

        consents.afterActiveRead = nil
        for kind in ConsentGateLogic.required {
            try? await consents.record(kind: kind, documentVersion: ConsentGateLogic.version(for: kind), source: "t")
        }
        gate.recheck()
        // A recheck that started nothing must fail here, not hang on the check it left held.
        #expect(gate.work != firstCheck)
        if gate.work != firstCheck { await gate.work?.value }
        #expect(gate.isPassed)

        release.open()
        await firstCheck?.value
        #expect(gate.isPassed)
    }
}
