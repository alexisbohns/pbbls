import Foundation
import Testing
@testable import Pebbles

/// A cover can be dropped without a word, or closed without the flow's end, and then nothing but these
/// bounds would notice. Every bound here runs out when the test says, so nothing races a clock.
@Suite("Onboarding cover recovery")
@MainActor
struct OnboardingCoverRecoveryTests {
    private static let user = "user-1"

    private let bounds = ManualBounds()
    private let service = FakeOnboardingService(completed: false)
    private let consents = FakeConsentService(rows: [])
    private let cache: ConsentPreferences
    private let consentGate = ConsentGateModel()
    private let presentation: OnboardingPresentation
    private var gate: OnboardingGateModel { presentation.gate }

    init() throws {
        let suite = "onboarding-cover-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        cache = ConsentPreferences(defaults: defaults)
        consentGate.cache = cache
        consentGate.service = consents
        presentation = OnboardingPresentation(gate: OnboardingGateModel(defaults: defaults), wait: bounds.wait)
        presentation.gate.service = service
    }

    /// The flag says the flow is wanted, and `RootView`'s edge asks for the cover.
    private func askForTheCover() async {
        gate.start(userId: Self.user)
        consentGate.start(userId: Self.user)
        await gate.work?.value
        await consentGate.work?.value
        presentation.presentCover()
    }

    /// The bound now running runs out, and whatever it decides has been decided.
    private func runOut() async {
        let watch = presentation.watch
        bounds.runOutNext()
        await watch?.value
    }

    /// The host says it is up, and the bound it beat runs out with nothing left to decide.
    private func coverComesUp() async {
        let watch = presentation.watch
        presentation.coverAppeared()
        bounds.runOutNext()
        await watch?.value
    }

    /// Asked for, never up, asked for again, never up again.
    private func giveUp() async {
        await askForTheCover()
        await runOut()
        await runOut()
        await runOut()
        await consentGate.work?.value
    }

    private func signOut() {
        presentation.reset()
        gate.start(userId: nil)
        consentGate.start(userId: nil)
    }

    private var layer: OnboardingConsentLayer { presentation.consentLayer(isPassed: consentGate.isPassed) }

    // MARK: never up

    @Test func aCoverThatNeverComesUpIsAskedForOnceMoreThenTheGateTakesOver() async {
        await askForTheCover()
        #expect(presentation.isPresenting)
        #expect(layer == .surface)

        await runOut()
        // Down first: SwiftUI has to see `false` before another `true` means anything.
        #expect(!presentation.isPresenting)
        #expect(layer == .surface)

        await runOut()
        #expect(presentation.isPresenting)
        #expect(layer == .surface)

        await runOut()
        #expect(!presentation.isPresenting)
        #expect(!gate.shouldPresent)
        #expect(!gate.isPending)
        await consentGate.work?.value
        #expect(layer == .gate)
        #expect(bounds.durations == [OnboardingBounds.coverUp, OnboardingBounds.coverRetry, OnboardingBounds.coverUp])
    }

    /// The form's pass is what opens the app then, and what was parked can come out.
    @Test func invitesWaitThroughTheRecoveryAndNotPastIt() async {
        await askForTheCover()
        #expect(presentation.invitesWait)
        await runOut()
        #expect(presentation.invitesWait)
        await runOut()
        #expect(presentation.invitesWait)
        await runOut()
        #expect(!presentation.invitesWait)
    }

    @Test func anAccountWithAPassLandsInTheApp() async {
        cache.markSatisfied(userId: Self.user, fingerprint: ConsentGateLogic.fingerprint())
        await giveUp()
        #expect(layer == .open)
    }

    /// Its form and Log out stay as they are: a re-read would swap them for a spinner for as long as a
    /// bad network takes, and `record_consent` doesn't mind being asked twice.
    @Test func givingUpLeavesTheGatesVerdictAlone() async {
        await askForTheCover()
        await runOut()
        await runOut()
        let reads = consents.activeCalls

        await runOut()

        #expect(consents.activeCalls == reads)
        #expect(consentGate.state == .required(.init(missing: ConsentGateLogic.required)))
        #expect(layer == .gate)
    }

    /// The act in flight finishes, and nothing after it goes out: the gate's form owns the ledger now.
    @Test func givingUpEndsTheFlowsSession() async {
        let harness = OnboardingFlowHarness()
        presentation.slot.sync(userId: Self.user) { harness.model(userId: $0) }
        await giveUp()
        #expect(presentation.slot.model == nil)
    }

    @Test func aCoverComingUpAfterGivingUpChangesNothing() async {
        await giveUp()
        presentation.coverAppeared()
        #expect(!presentation.isPresenting)
        #expect(!gate.shouldPresent)
        #expect(layer == .gate)
    }

    // MARK: up in time

    @Test func aCoverUpInTimeIsLeftAlone() async {
        await askForTheCover()

        await coverComesUp()

        #expect(presentation.isPresenting)
        #expect(gate.shouldPresent)
        #expect(layer == .surface)
        #expect(bounds.durations == [OnboardingBounds.coverUp])
    }

    @Test func aCoverUpOnTheSecondTryIsLeftAlone() async {
        await askForTheCover()
        await runOut()
        await runOut()

        await coverComesUp()

        #expect(presentation.isPresenting)
        #expect(gate.shouldPresent)
        #expect(layer == .surface)
    }

    /// The host appears again when the carve cover over it goes.
    @Test func aCoverReportingTwiceIsOneCover() async {
        await askForTheCover()
        await coverComesUp()
        presentation.coverAppeared()
        #expect(presentation.isPresenting)
        #expect(bounds.durations == [OnboardingBounds.coverUp])
    }

    // MARK: stale checks

    @Test func aCheckForAnEarlierPresentationDoesNothing() async {
        await askForTheCover()
        let stale = presentation.watch
        await bounds.reached(1)
        presentation.presentCover()

        bounds.runOut(0)
        await stale?.value

        // The second one's own bound is still running, and it counts from the start.
        #expect(presentation.isPresenting)
        #expect(gate.shouldPresent)
    }

    @Test func aCheckFromBeforeASignOutDoesNothingToTheNextSignIn() async {
        await askForTheCover()
        let stale = presentation.watch
        await bounds.reached(1)
        signOut()
        await askForTheCover()

        bounds.runOut(0)
        await stale?.value

        #expect(presentation.isPresenting)
        #expect(gate.shouldPresent)
    }

    /// The gate stopped wanting the flow before anything told the presentation.
    @Test func aCheckAfterTheGateStoppedWantingTheFlowOnlyLowersTheCover() async {
        await askForTheCover()
        gate.start(userId: nil)
        await runOut()
        #expect(!presentation.isPresenting)
        #expect(bounds.durations == [OnboardingBounds.coverUp])
    }

    // MARK: sign-out

    @Test func aSignOutWhileTheCoverIsRisingClearsEverything() async {
        await askForTheCover()
        let watch = presentation.watch

        signOut()
        bounds.runOutNext()
        await watch?.value

        #expect(!presentation.isPresenting)
        #expect(!presentation.invitesWait)
        #expect(bounds.durations == [OnboardingBounds.coverUp])
    }

    @Test func aSignOutBetweenTheTwoTriesClearsEverything() async {
        await askForTheCover()
        await runOut()
        let watch = presentation.watch

        signOut()
        bounds.runOutNext()
        await watch?.value

        #expect(!presentation.isPresenting)
        #expect(!presentation.invitesWait)
    }

    @Test func signingBackInAfterGivingUpStartsOver() async {
        await giveUp()
        signOut()

        await askForTheCover()
        #expect(presentation.isPresenting)
        #expect(layer == .surface)
        // A whole new retry to spend.
        await runOut()
        #expect(!presentation.isPresenting)
        await runOut()
        #expect(presentation.isPresenting)
        #expect(gate.shouldPresent)
    }

    // MARK: closed without the flow's end

    @Test func aCoverClosedWithoutTheFlowsEndIsAskedForOnceMoreThenTheGateTakesOver() async {
        await askForTheCover()
        await coverComesUp()

        // SwiftUI writing the binding.
        presentation.coverBinding = false
        #expect(!presentation.isPresenting)
        #expect(gate.shouldPresent)
        #expect(layer == .surface)

        await runOut()
        #expect(presentation.isPresenting)
        await coverComesUp()

        // `onDismiss`, on its own, is enough too.
        #expect(!presentation.coverDismissed())
        #expect(!presentation.isPresenting)
        #expect(!gate.shouldPresent)
        await consentGate.work?.value
        #expect(layer == .gate)
    }

    @Test func theBindingAndOnDismissOfOneClosingCountOnce() async {
        await askForTheCover()
        await coverComesUp()

        presentation.coverBinding = false
        #expect(!presentation.coverDismissed())

        #expect(gate.shouldPresent)
        await runOut()
        #expect(presentation.isPresenting)
    }

    /// Answers kept: the model lives on the presentation, and the same user keeps it.
    @Test func theFlowSurvivesBeingAskedForAgain() async {
        let harness = OnboardingFlowHarness()
        presentation.slot.sync(userId: Self.user) { harness.model(userId: $0) }
        let flow = presentation.slot.model
        flow?.setName("Léa")
        await askForTheCover()
        await coverComesUp()

        presentation.coverBinding = false
        await runOut()

        presentation.slot.sync(userId: Self.user) { harness.model(userId: $0) }
        #expect(presentation.slot.model === flow)
        #expect(presentation.slot.model?.name == "Léa")
    }

    /// A cover gone before it was ever up is the bound's to decide, not the binding's: one retry each.
    @Test func aBindingWrittenBeforeTheCoverWasUpLeavesItToTheBound() async {
        await askForTheCover()
        presentation.coverBinding = false
        #expect(!presentation.isPresenting)

        await runOut()
        #expect(bounds.durations.first == OnboardingBounds.coverUp)
        #expect(gate.shouldPresent)
        await runOut()
        #expect(presentation.isPresenting)
    }

    /// Its own `false` between the tries is not a cover that closed.
    @Test func onDismissBetweenTheTriesIsNotASecondLoss() async {
        await askForTheCover()
        await runOut()
        #expect(!presentation.coverDismissed())
        #expect(gate.shouldPresent)
        await runOut()
        #expect(presentation.isPresenting)
    }
}
