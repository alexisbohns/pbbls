import Foundation
import Testing
@testable import Pebbles

/// One flow per signed-in user (#1510 D10): what `OnboardingFlowHost` does on a user change.
@Suite("Onboarding flow slot")
@MainActor
struct OnboardingFlowSlotTests {
    private func make(_ harness: OnboardingFlowHarness) -> (String) -> OnboardingFlowModel {
        { harness.model(userId: $0) }
    }

    @Test func noUserHasNoModel() {
        let slot = OnboardingFlowSlot()
        slot.sync(userId: nil, make: make(OnboardingFlowHarness()))
        #expect(slot.model == nil)
    }

    @Test func theSameUserKeepsTheirModel() {
        let harness = OnboardingFlowHarness()
        let slot = OnboardingFlowSlot()
        slot.sync(userId: "a", make: make(harness))
        let first = slot.model
        first?.setName("Léa")
        slot.sync(userId: "a", make: make(harness))
        #expect(slot.model === first)
        #expect(slot.model?.name == "Léa")
    }

    @Test func aSecondAccountNeverSeesTheFirstsAnswers() {
        let harness = OnboardingFlowHarness()
        let slot = OnboardingFlowSlot()
        slot.sync(userId: "a", make: make(harness))
        let first = slot.model
        first?.setName("Léa")
        first?.toggleReason(.goodDays)

        slot.sync(userId: "b", make: make(harness))

        #expect(slot.model !== first)
        #expect(slot.model?.name == "")
        #expect(slot.model?.reasons == [])
    }

    @Test func signingOutDropsTheModel() {
        let harness = OnboardingFlowHarness()
        let slot = OnboardingFlowSlot()
        slot.sync(userId: "a", make: make(harness))
        slot.sync(userId: nil, make: make(harness))
        #expect(slot.model == nil)
    }

    /// The reason the host has to tell the old model: the next act would go out as whoever signs in next.
    @Test func aUserChangeStopsTheConsentsStillToGoOut() async {
        let harness = OnboardingFlowHarness()
        let entered = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            entered.open()
            await release.wait()
        }
        let slot = OnboardingFlowSlot()
        slot.sync(userId: "a", make: make(harness))
        let first = slot.model
        await first?.loadToConsent()
        first?.switchEveryActOn()
        first?.next()
        #expect(await entered.reached())

        slot.sync(userId: "b", make: make(harness))
        release.open()
        await first?.work?.value

        // Only the act already in flight at the change landed.
        #expect(harness.consents.recordCalls.count == 1)
    }

    @Test func aSignOutStopsTheConsentsStillToGoOut() async {
        let harness = OnboardingFlowHarness()
        let entered = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            entered.open()
            await release.wait()
        }
        let slot = OnboardingFlowSlot()
        slot.sync(userId: "a", make: make(harness))
        let first = slot.model
        await first?.loadToConsent()
        first?.switchEveryActOn()
        first?.next()
        #expect(await entered.reached())

        slot.sync(userId: nil, make: make(harness))
        release.open()
        await first?.work?.value

        #expect(harness.consents.recordCalls.count == 1)
        #expect(first?.consent.isSaving == false)
    }

    /// The note and the cache are written under the id the host hands in, and the gate reads the
    /// same cache under the same key: the casing has one source.
    @Test func theHostsIdKeysTheGatesCache() async throws {
        let harness = OnboardingFlowHarness()
        let id = try #require(UUID(uuidString: "ABCDEF01-2345-4678-9ABC-DEF012345678"))
        let key = try #require(OnboardingGate.userKey(id))
        let slot = OnboardingFlowSlot()
        slot.sync(userId: key, make: make(harness))
        await slot.model?.loadPastConsent()
        #expect(harness.cache.isSatisfied(userId: key, fingerprint: ConsentGateLogic.fingerprint()))

        let gate = ConsentGateModel()
        gate.cache = harness.cache
        gate.service = harness.consents
        gate.start(userId: key)
        await gate.work?.value
        #expect(gate.isPassed)
    }

    @Test func theNoteCarriesTheHostsId() async throws {
        let harness = OnboardingFlowHarness()
        let id = try #require(UUID(uuidString: "ABCDEF01-2345-4678-9ABC-DEF012345678"))
        let key = try #require(OnboardingGate.userKey(id))
        let slot = OnboardingFlowSlot()
        slot.sync(userId: key, make: make(harness))
        let model = try #require(slot.model)
        await model.loadPastConsent()
        model.toggleReason(.hardSeason)
        model.seedStep(.pace)
        model.next()
        await model.work?.value
        #expect(model.isDone)
        #expect(model.doneNote?.userId == key)
        #expect(model.doneNote?.reason == .hardSeason)
    }
}
