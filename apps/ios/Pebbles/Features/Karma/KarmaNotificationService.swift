import Foundation

/// Feature-agnostic entry point for the "+N karma" flash. Mirrors web's
/// `notifyKarma`: any credit source calls `notifyEarned(amount:reason:)`.
/// Delight only — never authoritative over the karma balance.
///
/// Presentation is a bottom-center in-app pastille (see `KarmaEarnedCapsule`),
/// which works on every device and iOS 17+ version. (The ActivityKit / Dynamic
/// Island stack is retained for a future Glyph-purchase notification, but is
/// not used here: iOS won't render a foreground app's own Live Activity, and
/// karma is always earned by a foreground action.)
@Observable
@MainActor
final class KarmaNotificationService {
    /// Content currently shown in the in-app pastille (nil = hidden). Observed
    /// by the karma overlay window.
    private(set) var activeCapsule: KarmaEarnedContent?

    /// Counts how many times feedback (haptic + sound) has fired. Observable so
    /// tests can assert the guard without reaching into CoreHaptics/audio.
    private(set) var hapticTrigger: Int = 0

    private let audio: AudioService
    private let haptics: HapticsService
    private var capsuleDismissTask: Task<Void, Never>?

    /// How long the pastille stays up. Tune-on-device. Exposed so the pastille's
    /// countdown ring can drain over exactly this window.
    let capsuleDuration: Duration = .milliseconds(2500)

    init(audio: AudioService = AudioService(),
         haptics: HapticsService = HapticsService()) {
        self.audio = audio
        self.haptics = haptics
    }

    func notifyEarned(amount: Int, reason: KarmaReason) {
        // Only positive credits celebrate; deletions/clawbacks stay silent.
        guard amount > 0 else { return }

        // Fire haptic + sound together so the waveform-matched vibration lands
        // in sync with the ceramic sound.
        hapticTrigger &+= 1
        haptics.playKarmaEarned()
        audio.playKarmaEarnedSound()

        presentCapsule(KarmaEarnedContent(amount: amount, reason: reason))
    }

    private func presentCapsule(_ content: KarmaEarnedContent) {
        activeCapsule = content
        capsuleDismissTask?.cancel()
        capsuleDismissTask = Task { [weak self, capsuleDuration] in
            try? await Task.sleep(for: capsuleDuration)
            guard !Task.isCancelled else { return }
            self?.activeCapsule = nil
        }
    }

    /// Called when the user taps the pastille.
    func dismissCapsule() {
        capsuleDismissTask?.cancel()
        activeCapsule = nil
    }
}
