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
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults))
        presentation.gate.service = service
    }

    /// As far as the cover being up, with a read and a ledger answering.
    private func present(_ consents: FakeConsentService) async {
        consentGate.service = consents
        presentation.gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await presentation.gate.work?.value
        await consentGate.work?.value
        presentation.isPresenting = true
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
