import Foundation
import Testing
@testable import Pebbles

/// The flow has ended, and `onDismiss` may never say the cover went: finishing while backgrounded, say.
/// Nothing may stay parked on it.
@Suite("Onboarding cover closing")
@MainActor
struct OnboardingCoverClosingTests {
    private let note = OnboardingHandoffNote(userId: "user-1", reason: .goodDays)
    private let bounds = ManualBounds()
    private let consentGate = ConsentGateModel()
    private let presentation: OnboardingPresentation

    init() throws {
        let suite = "onboarding-closing-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        consentGate.cache = ConsentPreferences(defaults: defaults)
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: bounds.wait)
    }

    private func runOut() async {
        let watch = presentation.watch
        bounds.runOutNext()
        await watch?.value
    }

    @Test func theHandOffRunsAtTheBoundWhenOnDismissNeverComes() async {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        #expect(presentation.isClosing)
        #expect(presentation.invitesWait)
        #expect(handOffs == 0)

        await runOut()

        #expect(handOffs == 1)
        #expect(!presentation.isClosing)
        #expect(!presentation.invitesWait)
        #expect(bounds.durations == [OnboardingBounds.closing])
        // Nothing stayed parked: the note reaches Home, the invite would open.
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
        #expect(presentation.handoff.take(userId: "user-1") == note)
    }

    @Test func aLateOnDismissDoesNotRunItTwice() async {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        await runOut()

        #expect(!presentation.coverDismissed())

        #expect(handOffs == 1)
    }

    @Test func aLateBoundDoesNotRunItTwice() async {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        let stale = presentation.watch

        #expect(presentation.coverDismissed())
        #expect(handOffs == 1)
        bounds.runOut(0)
        await stale?.value

        #expect(handOffs == 1)
    }

    @Test func aSecondOnDismissDoesNotRunItTwice() {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        #expect(presentation.coverDismissed())
        #expect(!presentation.coverDismissed())
        #expect(handOffs == 1)
    }

    @Test func aSignOutDuringTheClosingLeavesNoHandOff() async {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        let stale = presentation.watch

        presentation.reset()
        bounds.runOut(0)
        await stale?.value

        #expect(handOffs == 0)
        #expect(!presentation.isClosing)
        #expect(presentation.openWaiting(parkedInvite: nil) == nil)
    }

    /// A replay's second closing keeps its own bound.
    @Test func aBoundFromAnEarlierClosingDoesNotCutTheNextOneShort() async {
        var handOffs = 0
        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        let stale = presentation.watch
        await bounds.reached(1)
        #expect(presentation.coverDismissed())

        presentation.finish(note: note, consentGate: consentGate) { handOffs += 1 }
        bounds.runOut(0)
        await stale?.value

        #expect(presentation.isClosing)
        #expect(handOffs == 1)
    }

    /// The flow stays drawn while the cover slides, and goes with the hand-off however it comes.
    @Test func theFlowGoesWithTheHandOff() async {
        let harness = OnboardingFlowHarness()
        presentation.slot.sync(userId: "user-1") { harness.model(userId: $0) }
        presentation.finish(note: note, consentGate: consentGate)
        #expect(presentation.slot.model != nil)
        await runOut()
        #expect(presentation.slot.model == nil)
    }
}
