import Observation
import os

/// The onboarding's share of `RootView`: whether its cover is up, what the flow left behind, and what
/// stands above the tabs meanwhile. Apart from the view so its sequences are tested: the order at the
/// flow's end, one presentation after it, a sign-out leaving nothing for the next account, and a cover
/// that doesn't do what it was asked.
///
/// Nothing else watches the cover. A presentation can be dropped without a word (a token exchange
/// finishing inside `ASWebAuthenticationSession`'s dismissal, say), and it can close without the flow's
/// end, and either would leave the layer on `.surface` for good. So the host reports that it is up, a
/// cover that doesn't is asked for once more, and past that the flow stands down for this launch and
/// the consent gate decides, with its form and Log out.
@Observable
@MainActor
final class OnboardingPresentation {
    /// Where the cover stands. Every state but `idle` and `up` has a bound running.
    private enum CoverPhase {
        /// Not wanted: before the flow, past its end, or given up on.
        case idle
        /// Asked for, and the host hasn't said it is up.
        case rising
        /// The host said so.
        case up
        /// Taken down, to be asked for once more.
        case retrying
        /// From the flow's end to the hand-off: nothing else presents while the cover is going.
        case closing
    }

    let gate: OnboardingGateModel
    /// The flow's model, here and not in the cover's content: this object is always alive, and SwiftUI
    /// gives no guarantee the content hears a sign-out before the branch presenting it goes.
    let slot = OnboardingFlowSlot()
    /// What Home reads when the flow has ended.
    let handoff = OnboardingHandoff()
    /// Whether the cover is asked for. SwiftUI goes through `coverBinding`.
    private(set) var isPresenting = false
    private var phase = CoverPhase.idle
    /// The running bound, so tests can await what it decides.
    @ObservationIgnored private(set) var watch: Task<Void, Never>?
    /// Bumped whenever a bound stops mattering. It only acts if its token is still current.
    @ObservationIgnored private var watchToken = 0
    /// This presentation has had its second try.
    @ObservationIgnored private var hasRetried = false
    /// What follows the cover's going. Run once, by whichever comes first: `onDismiss` or the bound.
    @ObservationIgnored private var onClosed: (@MainActor () -> Void)?
    /// The flow's note, kept from its end until the composer can open.
    @ObservationIgnored private var note: OnboardingHandoffNote?
    /// The flow has ended and Home has not been told. Apart from the note, which an invite can spend.
    @ObservationIgnored private var homeIsStale = false
    @ObservationIgnored private let wait: OnboardingWait
    /// Unlock cards wait while the flow is up (#1565). Held from the cover going up; released by
    /// every exit: the flow ending with no composer, the composer (through `handoff`), the flow
    /// standing down, and a sign-out (which also clears them).
    @ObservationIgnored private weak var cards: AchievementNotificationService?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "onboarding")

    /// Not a default argument for the gate: those are evaluated outside the main actor.
    init(gate: OnboardingGateModel? = nil, wait: @escaping OnboardingWait = { try await Task.sleep(for: $0) }) {
        self.gate = gate ?? OnboardingGateModel()
        self.wait = wait
    }

    /// From the flow's end to the cover's `onDismiss`, or the bound on it.
    var isClosing: Bool { phase == .closing }

    /// The cover's binding. SwiftUI only ever writes `false`, which, with the host having said it was
    /// up and the flow not over, is a cover that closed without `finish`.
    var coverBinding: Bool {
        get { isPresenting }
        set {
            guard !newValue else { return }
            isPresenting = false
            if phase == .up { coverLost() }
        }
    }

    func consentLayer(isPassed: Bool) -> OnboardingConsentLayer {
        OnboardingGate.consentLayer(
            isPassed: isPassed,
            isPending: gate.isPending,
            shouldPresent: gate.shouldPresent,
            isPresenting: isPresenting
        )
    }

    var invitesWait: Bool {
        OnboardingGate.invitesWait(
            isPending: gate.isPending,
            shouldPresent: gate.shouldPresent,
            isPresenting: isPresenting,
            isClosing: isClosing
        )
    }

    // MARK: - Up, or not

    func bind(cards: AchievementNotificationService) {
        self.cards = cards
        handoff.cards = cards
    }

    /// The flag says the flow is wanted: ask for the cover, and start watching for it.
    func presentCover() {
        cards?.hold()
        hasRetried = false
        askForCover()
    }

    /// The host is on screen. It says so again whenever the carve cover over it goes, and a cover
    /// turning up late, after it stopped being wanted, is SwiftUI's to take down.
    func coverAppeared() {
        guard phase == .rising, isPresenting else { return }
        phase = .up
        disarm()
    }

    private func askForCover() {
        phase = .rising
        isPresenting = true
        arm(OnboardingBounds.coverUp)
    }

    /// The cover should be up and isn't. Down first: SwiftUI has to see `false` before another `true`
    /// means anything, and whatever dropped this one has to be over before the next.
    private func coverLost() {
        disarm()
        isPresenting = false
        guard gate.shouldPresent else {
            phase = .idle
            cards?.release()
            return
        }
        guard !hasRetried else {
            standDown()
            return
        }
        hasRetried = true
        phase = .retrying
        arm(OnboardingBounds.coverRetry)
    }

    /// Past the second try. What is left of the flow stops writing, since the gate's form owns the
    /// ledger now. The gate's verdict stays as it is, even if step 2 got ahead of it: a re-read would
    /// swap its controls for a spinner for as long as a bad network takes, and `record_consent` doesn't
    /// mind being asked twice.
    private func standDown() {
        logger.error("onboarding cover could not be kept up; the consent gate decides")
        phase = .idle
        slot.end()
        gate.standDown()
        cards?.release()
    }

    private func arm(_ bound: Duration) {
        disarm()
        let token = watchToken
        watch = Task { [weak self, wait] in
            do { try await wait(bound) } catch { return }
            guard let self, token == self.watchToken else { return }
            self.boundRanOut()
        }
    }

    private func disarm() {
        watch?.cancel()
        watchToken += 1
    }

    private func boundRanOut() {
        switch phase {
        case .rising:
            coverLost()
        case .retrying:
            guard gate.shouldPresent else {
                phase = .idle
                cards?.release()
                return
            }
            askForCover()
        case .closing:
            logger.notice("onboarding cover's onDismiss never came; handing off anyway")
            coverDismissed()
        case .idle, .up:
            break
        }
    }

    // MARK: - The flow's end

    /// The flow's end. The re-check comes before anything else moves: a pass recorded in step 2 opens
    /// the app in this same turn, so the gate never flashes under the closing cover. `onClosed` runs
    /// when the cover has gone, or at the bound: `onDismiss` may not come (finishing while backgrounded,
    /// say), and the composer and the invites must not stay parked on it.
    func finish(
        note: OnboardingHandoffNote?,
        consentGate: ConsentGateModel,
        onClosed: @escaping @MainActor () -> Void = {}
    ) {
        self.note = note
        self.onClosed = onClosed
        homeIsStale = true
        phase = .closing
        consentGate.recheck()
        gate.finish()
        isPresenting = false
        arm(OnboardingBounds.closing)
    }

    /// The cover is gone. True when that was the flow's end, the one time: a late `onDismiss` after the
    /// bound is false, as is one that came with a sign-out. Gone otherwise, it was lost.
    @discardableResult
    func coverDismissed() -> Bool {
        guard isClosing else {
            if phase == .up { coverLost() }
            return false
        }
        phase = .idle
        disarm()
        slot.end()
        let closed = onClosed
        onClosed = nil
        closed?()
        return true
    }

    /// Past every gate, one presentation: the parked invite to open, if there is one, or the note
    /// left for Home. Either way the note is spent, and Home hears that the flow ended.
    func openWaiting(parkedInvite: String?) -> String? {
        let waiting = OnboardingGate.handOff(parkedInvite: parkedInvite, note: note)
        let homeWasStale = homeIsStale
        note = nil
        homeIsStale = false
        switch waiting {
        case .composer(let note):
            handoff.leave(note)
            return nil
        case .invite(let token):
            if homeWasStale { handoff.dropNote() }
            cards?.release()
            return token
        case .nothing:
            if homeWasStale { handoff.dropNote() }
            cards?.release()
            return nil
        }
    }

    /// Signed out: the cover went with the authed branch. The device flag is left alone, so a flow
    /// abandoned by logging out restarts next time.
    func reset() {
        disarm()
        phase = .idle
        hasRetried = false
        onClosed = nil
        isPresenting = false
        note = nil
        homeIsStale = false
        _ = handoff.take(userId: nil)
        slot.end()
        cards?.reset()
    }
}
