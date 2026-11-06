import Observation

/// The onboarding's share of `RootView`: whether its cover is up, what the flow left behind, and what
/// stands above the tabs meanwhile. Apart from the view so its sequences are tested: the order at the
/// flow's end, one presentation after it, and a sign-out leaving nothing for the next account.
@Observable
@MainActor
final class OnboardingPresentation {
    let gate: OnboardingGateModel
    /// The cover's binding.
    var isPresenting = false
    /// From the flow's end to the cover's `onDismiss`: nothing else presents while the cover is going.
    private(set) var isClosing = false
    /// The flow's note, kept from its end until the composer can open.
    @ObservationIgnored private var note: OnboardingHandoffNote?

    /// Not a default argument: those are evaluated outside the main actor.
    init(gate: OnboardingGateModel? = nil) {
        self.gate = gate ?? OnboardingGateModel()
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

    /// The flow's end. The re-check comes before anything else moves: a pass recorded in step 2 opens
    /// the app in this same turn, so the gate never flashes under the closing cover.
    func finish(note: OnboardingHandoffNote?, consentGate: ConsentGateModel) {
        self.note = note
        isClosing = true
        consentGate.recheck()
        gate.finish()
        isPresenting = false
    }

    /// The cover is gone. True when that was the flow's end, false when it went with a sign-out.
    func coverDismissed() -> Bool {
        guard isClosing else { return false }
        isClosing = false
        return true
    }

    /// Past every gate, one presentation: the parked invite to open, if there is one. Either way the
    /// note is spent.
    func openWaiting(parkedInvite: String?) -> String? {
        let waiting = OnboardingGate.handOff(parkedInvite: parkedInvite, note: note)
        note = nil
        switch waiting {
        case .invite(let token): return token
        case .composer, .nothing: return nil
        }
    }

    /// Signed out: the cover went with the authed branch. The device flag is left alone, so a flow
    /// abandoned by logging out restarts next time.
    func reset() {
        isPresenting = false
        isClosing = false
        note = nil
    }
}
