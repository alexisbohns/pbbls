import Foundation
import Testing
@testable import Pebbles

/// The ledger read holds the app, so it has a ceiling (`ConsentGateModel.ledgerRead`, #1567), which
/// runs out here when the test says.
@Suite("Consent gate ceiling")
@MainActor
struct ConsentGateCeilingTests {
    let cache: ConsentPreferences

    init() throws {
        let suite = "consent-gate-ceiling-\(UUID().uuidString)"
        cache = ConsentPreferences(defaults: try #require(UserDefaults(suiteName: suite)))
    }

    /// A gate whose ceiling runs out when the test says, over reads held until `held` opens.
    func model(_ consents: FakeConsentService, bounds: ManualBounds, held: Latch) -> ConsentGateModel {
        consents.beforeActiveRead = { await held.wait() }
        let model = ConsentGateModel(wait: bounds.wait)
        model.service = consents
        model.cache = cache
        return model
    }

    @Test("a read that never answers fails closed at the ceiling")
    func ceilingFailsClosed() async {
        let bounds = ManualBounds()
        let held = Latch()
        let gate = model(FakeConsentService(), bounds: bounds, held: held)
        gate.start(userId: "user-1")
        #expect(gate.state == .checking)

        bounds.runOutNext()
        await gate.ceiling?.value

        #expect(gate.state == .failed(.offline))
        #expect(!gate.isPassed)
        #expect(bounds.durations == [ConsentGateModel.ledgerRead])

        // The read still out answers late, and changes nothing.
        held.open()
        await gate.work?.value
        #expect(gate.state == .failed(.offline))
    }

    @Test("a read that answers first is not undone by its ceiling")
    func answerBeatsCeiling() async {
        let bounds = ManualBounds()
        let held = Latch()
        held.open()
        let gate = model(FakeConsentService(), bounds: bounds, held: held)
        gate.start(userId: "user-1")
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-1"))

        let ceiling = gate.ceiling
        await bounds.reached(1)
        bounds.runOutNext()
        await ceiling?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
    }

    @Test("try again after the ceiling reads again, under a fresh ceiling")
    func retryAfterCeiling() async {
        let bounds = ManualBounds()
        let held = Latch()
        let consents = FakeConsentService()
        let gate = model(consents, bounds: bounds, held: held)
        gate.start(userId: "user-1")
        bounds.runOutNext()
        await gate.ceiling?.value
        #expect(gate.state == .failed(.offline))

        held.open()
        gate.retry()
        await gate.work?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
        #expect(consents.activeCalls == 2)

        // The first ceiling already ran out; the second one running out now changes nothing.
        await bounds.reached(2)
        let ceiling = gate.ceiling
        bounds.runOut(1)
        await ceiling?.value
        #expect(gate.state == .satisfied(userId: "user-1"))
    }

    @Test("a ceiling for the last user never fails the next one")
    func ceilingIsBoundToItsUser() async {
        let bounds = ManualBounds()
        let held = Latch()
        let gate = model(FakeConsentService(), bounds: bounds, held: held)
        gate.start(userId: "user-1")
        let first = gate.ceiling
        await bounds.reached(1)

        gate.start(userId: "user-2")
        bounds.runOut(0)
        await first?.value
        #expect(gate.state == .checking)
    }
}
