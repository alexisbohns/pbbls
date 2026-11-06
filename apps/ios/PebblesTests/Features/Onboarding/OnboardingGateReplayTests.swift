#if DEBUG
import Foundation
import Testing
@testable import Pebbles

/// The debug row's device half: the flow can be shown again without a restart. The profile write in
/// `replay(client:)` needs a server, and is left to the device pass.
@Suite("Onboarding gate replay")
@MainActor
struct OnboardingGateReplayTests {
    private let service = FakeOnboardingService(completed: false)
    private let defaults: UserDefaults
    private let gate: OnboardingGateModel

    init() throws {
        let suite = "onboarding-replay-\(UUID().uuidString)"
        defaults = try #require(UserDefaults(suiteName: suite))
        gate = OnboardingGateModel(defaults: defaults)
        gate.service = service
    }

    @Test func aFinishedFlowPresentsAgain() async {
        gate.start(userId: "a")
        await gate.work?.value
        gate.finish()
        #expect(!gate.shouldPresent)
        #expect(defaults.bool(forKey: OnboardingGateModel.hasSeenKey))

        gate.forgetForReplay()

        #expect(!defaults.bool(forKey: OnboardingGateModel.hasSeenKey))
        #expect(!gate.hasSeen)
        // Pending first: the loader holds, and the consent gate stands down, until the read lands.
        #expect(gate.isPending)
        await gate.work?.value
        #expect(gate.shouldPresent)
    }

    @Test func aDeviceThatSkippedTheReadNowReadsTheProfile() async {
        defaults.set(true, forKey: OnboardingGateModel.hasSeenKey)
        let seen = OnboardingGateModel(defaults: defaults)
        seen.service = service
        seen.start(userId: "a")
        #expect(service.completedCalls == 0)

        seen.forgetForReplay()
        await seen.work?.value

        #expect(service.completedCalls == 1)
        #expect(seen.shouldPresent)
    }

    @Test func aFailedReadPresentsNothing() async {
        gate.start(userId: "a")
        await gate.work?.value
        gate.finish()
        service.completedFailure = URLError(.notConnectedToInternet)

        gate.forgetForReplay()
        await gate.work?.value

        #expect(gate.readFailed)
        #expect(!gate.shouldPresent)
        #expect(!gate.isPending)
    }

    @Test func aFailedReadBeforeTheReplayIsForgotten() async {
        service.completedFailure = URLError(.notConnectedToInternet)
        gate.start(userId: "a")
        await gate.work?.value
        #expect(gate.readFailed)
        service.completedFailure = nil

        gate.forgetForReplay()
        #expect(!gate.readFailed)
        await gate.work?.value

        #expect(gate.shouldPresent)
    }

    /// The replay's read has the same ceiling: Settings' row can't hold the app either.
    @Test func aReplayReadThatNeverAnswersFailsAtTheCeiling() async {
        let bounds = ManualBounds()
        let held = Latch()
        defer { held.open() }
        service.onCompleted = { _ in
            await held.wait()
            return false
        }
        let timed = OnboardingGateModel(defaults: defaults, wait: bounds.wait)
        timed.service = service
        timed.start(userId: "a")
        bounds.runOut(0)
        await timed.ceiling?.value
        timed.forgetForReplay()
        #expect(timed.isPending)

        let ceiling = timed.ceiling
        bounds.runOut(1)
        await ceiling?.value

        #expect(timed.readFailed)
        #expect(!timed.isPending)
    }

    /// After a sign-out there is nobody to replay for.
    @Test func withNoUserNothingIsRead() async {
        gate.start(userId: "a")
        await gate.work?.value
        gate.start(userId: nil)
        let reads = service.completedCalls

        gate.forgetForReplay()
        await gate.work?.value

        #expect(service.completedCalls == reads)
        #expect(!gate.shouldPresent)
        #expect(!gate.isPending)
    }

    /// The presentation's half: after the replay the next cover gets a new flow, not the finished one.
    @Test func theNextCoverGetsAFreshFlow() async throws {
        let presentation = OnboardingPresentation(gate: gate)
        let harness = OnboardingFlowHarness()
        let consentGate = ConsentGateModel()
        let suite = "onboarding-replay-consent-\(UUID().uuidString)"
        consentGate.cache = ConsentPreferences(defaults: try #require(UserDefaults(suiteName: suite)))
        presentation.slot.sync(userId: "a") { harness.model(userId: $0) }
        let first = presentation.slot.model
        gate.start(userId: "a")
        await gate.work?.value
        presentation.isPresenting = true

        presentation.finish(note: nil, consentGate: consentGate)
        #expect(presentation.coverDismissed())
        gate.forgetForReplay()
        await gate.work?.value

        #expect(gate.shouldPresent)
        presentation.slot.sync(userId: "a") { harness.model(userId: $0) }
        #expect(presentation.slot.model !== first)
    }
}
#endif
