import Foundation
import Testing
@testable import Pebbles

/// The decisions `RootView` draws, written out in full rather than re-derived, so a change to any
/// one combination shows up here. The consent layer is the app's fail-closed rule.
@Suite("Onboarding root gate")
struct OnboardingRootGateTests {
    private struct LayerRow {
        let passed, pending, should, presenting: Bool
        let layer: OnboardingConsentLayer
    }

    private static let layers: [LayerRow] = [
        // A pass opens the app whatever the flow is doing: the cover is what covers it then.
        LayerRow(passed: true, pending: false, should: false, presenting: false, layer: .open),
        LayerRow(passed: true, pending: false, should: false, presenting: true, layer: .open),
        LayerRow(passed: true, pending: false, should: true, presenting: false, layer: .open),
        LayerRow(passed: true, pending: false, should: true, presenting: true, layer: .open),
        LayerRow(passed: true, pending: true, should: false, presenting: false, layer: .open),
        LayerRow(passed: true, pending: true, should: false, presenting: true, layer: .open),
        LayerRow(passed: true, pending: true, should: true, presenting: false, layer: .open),
        LayerRow(passed: true, pending: true, should: true, presenting: true, layer: .open),
        // No pass and no flow in play: the gate, unchanged for everyone else.
        LayerRow(passed: false, pending: false, should: false, presenting: false, layer: .gate),
        // No pass, and the flow pending, about to present or presenting: opaque, and never the gate.
        LayerRow(passed: false, pending: false, should: false, presenting: true, layer: .surface),
        LayerRow(passed: false, pending: false, should: true, presenting: false, layer: .surface),
        LayerRow(passed: false, pending: false, should: true, presenting: true, layer: .surface),
        LayerRow(passed: false, pending: true, should: false, presenting: false, layer: .surface),
        LayerRow(passed: false, pending: true, should: false, presenting: true, layer: .surface),
        LayerRow(passed: false, pending: true, should: true, presenting: false, layer: .surface),
        LayerRow(passed: false, pending: true, should: true, presenting: true, layer: .surface)
    ]

    @Test func theConsentLayerForEveryCombination() {
        #expect(Self.layers.count == 16)
        for row in Self.layers {
            let layer = OnboardingGate.consentLayer(
                isPassed: row.passed,
                isPending: row.pending,
                shouldPresent: row.should,
                isPresenting: row.presenting
            )
            #expect(layer == row.layer, "\(row)")
        }
    }

    @Test func withoutAPassTheTabsAreNeverOpen() {
        for row in Self.layers where !row.passed {
            let layer = OnboardingGate.consentLayer(
                isPassed: false,
                isPending: row.pending,
                shouldPresent: row.should,
                isPresenting: row.presenting
            )
            #expect(layer != .open, "\(row)")
        }
    }

    @Test func invitesWaitForTheFlowsEnd() {
        let flags = [false, true]
        for pending in flags {
            for should in flags {
                for presenting in flags {
                    for closing in flags {
                        let waits = OnboardingGate.invitesWait(
                            isPending: pending,
                            shouldPresent: should,
                            isPresenting: presenting,
                            isClosing: closing
                        )
                        let anyInPlay = pending || should || presenting || closing
                        #expect(waits == anyInPlay, "\(pending) \(should) \(presenting) \(closing)")
                    }
                }
            }
        }
        // The one combination that lets an invite through, spelled out.
        #expect(!OnboardingGate.invitesWait(
            isPending: false, shouldPresent: false, isPresenting: false, isClosing: false
        ))
    }

    @Test func aParkedInviteWinsOverTheComposer() {
        let note = OnboardingHandoffNote(userId: "u", reason: .goodDays)
        #expect(OnboardingGate.handOff(parkedInvite: "t", note: note) == .invite("t"))
        #expect(OnboardingGate.handOff(parkedInvite: "t", note: nil) == .invite("t"))
        #expect(OnboardingGate.handOff(parkedInvite: nil, note: note) == .composer(note))
        #expect(OnboardingGate.handOff(parkedInvite: nil, note: nil) == .nothing)
    }

    @Test func theUserKeyIsLowercased() throws {
        let id = try #require(UUID(uuidString: "ABCDEF01-2345-4678-9ABC-DEF012345678"))
        #expect(OnboardingGate.userKey(id) == "abcdef01-2345-4678-9abc-def012345678")
        #expect(OnboardingGate.userKey(nil) == nil)
    }
}

/// The two real gates side by side on fakes, as `RootView` starts them: what the consent layer
/// says at each moment a person goes through.
@Suite("Onboarding root gate scenarios")
@MainActor
struct OnboardingRootGateScenarioTests {
    private static let user = "user-1"

    let service = FakeOnboardingService()
    let cache: ConsentPreferences
    let onboarding: OnboardingGateModel
    let consentGate = ConsentGateModel()

    init() throws {
        let suite = "onboarding-root-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        cache = ConsentPreferences(defaults: defaults)
        onboarding = OnboardingGateModel(defaults: defaults)
        consentGate.cache = cache
    }

    private func start(_ consents: FakeConsentService, seen: Bool = false) {
        if seen { onboarding.finish() }
        onboarding.service = service
        consentGate.service = consents
        onboarding.start(userId: Self.user)
        consentGate.start(userId: Self.user)
    }

    private func layer(presenting: Bool = false) -> OnboardingConsentLayer {
        OnboardingGate.consentLayer(
            isPassed: consentGate.isPassed,
            isPending: onboarding.isPending,
            shouldPresent: onboarding.shouldPresent,
            isPresenting: presenting
        )
    }

    private func flowEnds() {
        consentGate.recheck()
        onboarding.finish()
    }

    @Test func aNewAccountMeetsThePromiseBeforeTheConsent() async {
        let held = Latch()
        service.onCompleted = { _ in
            await held.wait()
            return false
        }
        start(FakeConsentService(rows: []))
        // The ledger's verdict lands while the flag is still unknown: the gate's form must not show.
        await consentGate.work?.value
        #expect(!consentGate.isPassed)
        #expect(onboarding.isPending)
        #expect(layer() == .surface)

        held.open()
        await onboarding.work?.value
        #expect(onboarding.shouldPresent)
        // The frame between the flag landing and the cover's state being set.
        #expect(layer() == .surface)
        #expect(layer(presenting: true) == .surface)
    }

    @Test func aPreFlowAccountWithAMissingActStillMeetsTheGate() async {
        start(FakeConsentService(rows: []), seen: true)
        await consentGate.work?.value
        #expect(service.completedCalls == 0)
        #expect(!onboarding.shouldPresent)
        #expect(layer() == .gate)
    }

    @Test func aCompletedAccountOnANewDeviceIsNotPresented() async {
        service.completed = true
        start(FakeConsentService(rows: []))
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(!onboarding.shouldPresent)
        #expect(!onboarding.hasSeen)
        #expect(layer() == .gate)
    }

    @Test func aFailedFlagReadLeavesItToTheConsentGate() async {
        service.completedFailure = URLError(.notConnectedToInternet)
        start(FakeConsentService(rows: []))
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(onboarding.readFailed)
        #expect(!onboarding.shouldPresent)
        #expect(layer() == .gate)
    }

    @Test func aFailedFlagReadWithConsentOnRecordOpensTheApp() async {
        service.completedFailure = URLError(.notConnectedToInternet)
        start(FakeConsentService())
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(layer() == .open)
    }

    @Test func aFailedLedgerReadNeverOpensTheTabsUnderTheFlow() async {
        let consents = FakeConsentService()
        consents.activeFailure = URLError(.notConnectedToInternet)
        start(consents)
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(onboarding.shouldPresent)
        #expect(layer(presenting: true) == .surface)
    }

    /// The flow wrote the cache: the app opens in the same turn, so the gate never flashes.
    @Test func theFlowsEndOpensWithoutTheGateFlashing() async {
        let consents = FakeConsentService(rows: [])
        start(consents)
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(layer(presenting: true) == .surface)

        let flow = OnboardingFlowModel(
            service: service, consents: consents, cache: cache, userId: Self.user, haptic: { _ in }
        )
        await flow.loadPastConsent()
        let reads = consents.activeCalls

        flowEnds()

        #expect(consentGate.isPassed)
        #expect(layer() == .open)
        #expect(consents.activeCalls == reads)
        #expect(onboarding.hasSeen)
    }

    /// Nothing marked the cache (all four came from the ledger read, say): the gate checks, and its
    /// spinner is what shows, never the tabs.
    @Test func theFlowsEndWithoutACachedPassHoldsUntilTheCheckPasses() async {
        let consents = FakeConsentService(rows: [])
        start(consents)
        await onboarding.work?.value
        await consentGate.work?.value
        for kind in ConsentGateLogic.required {
            try? await consents.record(kind: kind, documentVersion: ConsentGateLogic.version(for: kind), source: "t")
        }

        flowEnds()

        #expect(consentGate.state == .checking)
        #expect(layer() == .gate)
        await consentGate.work?.value
        #expect(layer() == .open)
    }

    @Test func theFlowsEndWithAnActStillMissingIsHeldByTheGate() async {
        start(FakeConsentService(rows: []))
        await onboarding.work?.value
        await consentGate.work?.value

        flowEnds()
        await consentGate.work?.value

        #expect(!consentGate.isPassed)
        #expect(layer() == .gate)
    }

    @Test func theFlowsEndWithAnUnreadableLedgerFailsClosed() async {
        let consents = FakeConsentService(rows: [])
        start(consents)
        await onboarding.work?.value
        await consentGate.work?.value
        consents.activeFailure = URLError(.notConnectedToInternet)

        flowEnds()
        await consentGate.work?.value

        #expect(consentGate.state == .failed(.offline))
        #expect(layer() == .gate)
    }

    /// Sign-out while the flow is up: both gates forget, and the device flag stays as it was, so
    /// the flow restarts next time.
    @Test func signingOutMidFlowKeepsTheFlowForNextTime() async {
        start(FakeConsentService(rows: []))
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(onboarding.shouldPresent)

        onboarding.start(userId: nil)
        consentGate.start(userId: nil)
        #expect(!onboarding.hasSeen)
        #expect(!onboarding.shouldPresent)

        onboarding.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await onboarding.work?.value
        await consentGate.work?.value
        #expect(onboarding.shouldPresent)
        #expect(layer() == .surface)
    }
}
