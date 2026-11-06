import Foundation
import Testing
@testable import Pebbles

/// `RootView`'s onboarding state, step by step: the order at the flow's end and what waits for it.
@Suite("Onboarding presentation")
@MainActor
struct OnboardingPresentationTests {
    private static let user = "user-1"

    let service = FakeOnboardingService()
    let cache: ConsentPreferences
    let consentGate = ConsentGateModel()
    let presentation: OnboardingPresentation
    private let note = OnboardingHandoffNote(userId: Self.user, reason: .goodDays)

    init() throws {
        let suite = "onboarding-presentation-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        cache = ConsentPreferences(defaults: defaults)
        consentGate.cache = cache
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: ManualBounds().wait)
        presentation.gate.service = service
    }

    /// As far as the cover being up, with a read and a ledger answering.
    private func present(_ consents: FakeConsentService) async {
        consentGate.service = consents
        presentation.gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await presentation.gate.work?.value
        await consentGate.work?.value
        presentation.presentCover()
    }

    @Test func theLayerReadsTheCover() async {
        await present(FakeConsentService(rows: []))
        #expect(presentation.consentLayer(isPassed: false) == .surface)
        #expect(presentation.consentLayer(isPassed: true) == .open)
    }

    @Test func theFlowsEndClosesWithTheGateAlreadyDecided() async {
        await present(FakeConsentService(rows: []))
        cache.markSatisfied(userId: Self.user, fingerprint: ConsentGateLogic.fingerprint())

        presentation.finish(note: note, consentGate: consentGate)

        #expect(!presentation.isPresenting)
        #expect(presentation.isClosing)
        #expect(presentation.gate.hasSeen)
        #expect(!presentation.gate.shouldPresent)
        // In the same turn: nothing can draw the gate between the cover and the pass.
        #expect(consentGate.isPassed)
        #expect(presentation.consentLayer(isPassed: consentGate.isPassed) == .open)
    }

    @Test func invitesWaitUntilTheCoverIsGone() async {
        await present(FakeConsentService())
        #expect(presentation.invitesWait)
        presentation.finish(note: note, consentGate: consentGate)
        #expect(presentation.invitesWait)
        #expect(presentation.coverDismissed())
        #expect(!presentation.invitesWait)
    }

    /// The flag read is still out while the ledger has already passed: an invite must not get ahead.
    @Test func invitesWaitWhileTheFlagIsUnknown() async {
        let held = Latch()
        service.onCompleted = { _ in
            await held.wait()
            return true
        }
        consentGate.service = FakeConsentService()
        presentation.gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await consentGate.work?.value
        #expect(consentGate.isPassed)
        #expect(presentation.invitesWait)
        held.open()
        await presentation.gate.work?.value
        #expect(!presentation.invitesWait)
    }

    @Test func aCoverGoneWithASignOutIsNoHandOff() async {
        await present(FakeConsentService(rows: []))
        presentation.reset()
        #expect(!presentation.coverDismissed())
    }

    @Test func aParkedInviteOpensAndTheNoteIsSpent() async {
        await present(FakeConsentService())
        presentation.finish(note: note, consentGate: consentGate)
        _ = presentation.coverDismissed()

        #expect(presentation.openWaiting(parkedInvite: "token") == "token")
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
    }

    @Test func signingOutClearsTheCoverAndKeepsTheDeviceFlag() async {
        await present(FakeConsentService(rows: []))

        presentation.reset()
        presentation.gate.start(userId: nil)

        #expect(!presentation.isPresenting)
        #expect(!presentation.isClosing)
        #expect(!presentation.gate.hasSeen)
        #expect(!presentation.invitesWait)
    }

    @Test func aSignOutDuringTheClosingLeavesNothingToOpen() async {
        await present(FakeConsentService())
        presentation.finish(note: note, consentGate: consentGate)

        presentation.reset()

        #expect(!presentation.isClosing)
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
    }
}

/// What reaches Home after the flow, once everything is out of the way.
@Suite("Onboarding presentation hand-off")
@MainActor
struct OnboardingPresentationHandOffTests {
    private let note = OnboardingHandoffNote(userId: "user-1", reason: .goodDays)
    private let consentGate = ConsentGateModel()
    private let presentation: OnboardingPresentation

    init() throws {
        let suite = "onboarding-presentation-handoff-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        consentGate.cache = ConsentPreferences(defaults: defaults)
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: ManualBounds().wait)
    }

    /// The flow's end and the cover's `onDismiss`.
    private func flowEnds(note: OnboardingHandoffNote?) {
        presentation.finish(note: note, consentGate: consentGate)
        _ = presentation.coverDismissed()
    }

    @Test func nothingReachesHomeBeforeTheGatesAreClear() {
        presentation.finish(note: note, consentGate: consentGate)
        #expect(presentation.handoff.revision == 0)
    }

    @Test func withNoInviteTheNoteGoesToHome() {
        flowEnds(note: note)
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
        #expect(presentation.handoff.revision == 1)
        #expect(presentation.handoff.take(userId: "user-1") == note)
    }

    @Test func theNoteGoesOnce() {
        flowEnds(note: note)
        _ = presentation.openWaiting(parkedInvite: nil)
        _ = presentation.handoff.take(userId: "user-1")
        _ = presentation.openWaiting(parkedInvite: nil)
        #expect(presentation.handoff.revision == 1)
        #expect(presentation.handoff.take(userId: "user-1") == nil)
    }

    /// One presentation: no composer behind the invite, and Home still re-reads the name.
    @Test func aParkedInviteDropsTheNoteAndStillWakesHome() {
        flowEnds(note: note)
        #expect(presentation.openWaiting(parkedInvite: "token") == "token")
        #expect(presentation.handoff.revision == 1)
        #expect(presentation.handoff.take(userId: "user-1") == nil)
    }

    @Test func aFlowEndingWithoutANoteStillWakesHome() {
        flowEnds(note: nil)
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
        #expect(presentation.handoff.revision == 1)
    }

    /// An ordinary invite, with no flow anywhere near it, has nothing to say to Home.
    @Test func anInviteWithNoFlowLeavesHomeAlone() {
        #expect(presentation.openWaiting(parkedInvite: "token") == "token")
        #expect(presentation.handoff.revision == 0)
    }

    @Test func aSignOutEmptiesTheMailbox() {
        flowEnds(note: note)
        _ = presentation.openWaiting(parkedInvite: nil)
        presentation.reset()
        #expect(presentation.handoff.take(userId: "user-1") == nil)
    }
}

/// `RootView` is always alive and the cover's content may not be: SwiftUI gives no guarantee the host
/// hears a sign-out before the branch that presents it goes. So the presentation ends the flow too.
@Suite("Onboarding presentation flow")
@MainActor
struct OnboardingPresentationFlowTests {
    private let note = OnboardingHandoffNote(userId: "a", reason: nil)
    private let harness = OnboardingFlowHarness()
    private let consentGate = ConsentGateModel()
    private let presentation: OnboardingPresentation

    init() throws {
        let suite = "onboarding-presentation-flow-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        consentGate.cache = ConsentPreferences(defaults: defaults)
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: ManualBounds().wait)
    }

    private func sync(_ userId: String?) {
        presentation.slot.sync(userId: userId) { harness.model(userId: $0) }
    }

    @Test func aSignOutEndsTheFlowWhetherOrNotTheCoverHeardIt() async {
        let entered = Latch()
        let release = Latch()
        harness.consents.beforeRecord = {
            entered.open()
            await release.wait()
        }
        sync("a")
        let flow = presentation.slot.model
        await flow?.loadToConsent()
        flow?.switchEveryActOn()
        flow?.next()
        #expect(await entered.reached())

        presentation.reset()
        release.open()
        await flow?.work?.value

        #expect(presentation.slot.model == nil)
        // Only the act already in flight landed.
        #expect(harness.consents.recordCalls.count == 1)
    }

    /// The pace step stays drawn while the cover slides down.
    @Test func theFlowStaysOnScreenWhileTheCoverCloses() {
        sync("a")
        let flow = presentation.slot.model
        presentation.finish(note: note, consentGate: consentGate)
        #expect(presentation.slot.model === flow)
    }

    @Test func aReplayGetsAFreshFlowForTheSameUser() {
        sync("a")
        let flow = presentation.slot.model
        flow?.setName("Léa")
        presentation.finish(note: note, consentGate: consentGate)
        #expect(presentation.coverDismissed())
        #expect(presentation.slot.model == nil)

        sync("a")
        #expect(presentation.slot.model != nil)
        #expect(presentation.slot.model !== flow)
        #expect(presentation.slot.model?.name == "")
    }

    @Test func signingBackInAfterASignOutGetsAFreshFlow() {
        sync("a")
        let flow = presentation.slot.model
        presentation.reset()
        sync("a")
        #expect(presentation.slot.model != nil)
        #expect(presentation.slot.model !== flow)
    }
}
