import Foundation
import ActivityKit

/// Abstraction over the Live Activity path so the service is testable without
/// ActivityKit and so the controller can be injected after it exists (a later
/// task).
@MainActor
protocol KarmaLiveActivityPresenting: AnyObject {
    /// Presents/updates the Live Activity. Returns false if it could not be
    /// shown (disabled, throws) so the caller falls back to the capsule.
    func present(_ content: KarmaEarnedContent) async -> Bool
}

/// Feature-agnostic entry point for the "+N karma" flash. Mirrors web's
/// `notifyKarma`: any credit source calls `notifyEarned(amount:reason:)`.
/// Delight only — never authoritative over the karma balance.
@Observable
@MainActor
final class KarmaNotificationService {
    /// Content currently shown in the in-app capsule (nil = hidden). Observed
    /// by `RootView`.
    private(set) var activeCapsule: KarmaEarnedContent?

    /// Monotonic counter driving `.sensoryFeedback(.success, trigger:)`.
    private(set) var hapticTrigger: Int = 0

    /// Injected in a later task; nil means capsule-only.
    var liveActivityPresenter: KarmaLiveActivityPresenting?

    private let hasDynamicIsland: Bool
    private let audio: AudioService
    private var capsuleDismissTask: Task<Void, Never>?

    /// Seconds the capsule stays up. Tune-on-device; mirrors the Live Activity
    /// dismiss window so both paths feel the same.
    private let capsuleDuration: Duration = .milliseconds(2500)

    init(hasDynamicIsland: Bool = DeviceCapabilities.hasDynamicIsland,
         audio: AudioService = AudioService()) {
        self.hasDynamicIsland = hasDynamicIsland
        self.audio = audio
    }

    func notifyEarned(amount: Int, reason: KarmaReason) {
        let decision = karmaPresentationDecision(
            amount: amount,
            hasDynamicIsland: hasDynamicIsland,
            activitiesEnabled: ActivityAuthorizationInfo().areActivitiesEnabled
        )
        guard decision != .none else { return }

        hapticTrigger &+= 1
        audio.playKarmaEarnedSound()

        let content = KarmaEarnedContent(amount: amount, reason: reason)

        if decision == .liveActivity, let presenter = liveActivityPresenter {
            // Try the Live Activity; fall back to the capsule if it can't show.
            Task {
                let shown = await presenter.present(content)
                if !shown { presentCapsule(content) }
            }
        } else {
            presentCapsule(content)
        }
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

    /// Called when the user taps the capsule.
    func dismissCapsule() {
        capsuleDismissTask?.cancel()
        activeCapsule = nil
    }
}
