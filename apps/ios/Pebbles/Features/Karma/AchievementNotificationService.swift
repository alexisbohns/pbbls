import Foundation

/// One achievement unlock moment. Several unlocks in a single
/// `check_achievements()` response collapse into one content value (web
/// parity): `title` carries the localized badge title when exactly one badge
/// unlocked, else the capsule shows a count phrase. `karmaTotal` is the sum
/// the server actually emitted.
struct AchievementUnlockedContent: Equatable, Sendable {
    let count: Int
    let title: String?
    let karmaTotal: Int
}

/// Explicit-fire entry point for the achievement unlock capsule. Sibling of
/// `KarmaNotificationService` — same presentation contract (a bottom-center
/// pastille in the shared overlay window), its own slot so an achievement
/// moment and a karma flash fired by the same mutation coexist instead of one
/// replacing the other (the overlay stacks them; see `KarmaOverlayRoot`).
/// Delight only — never authoritative over what is unlocked.
@Observable
@MainActor
final class AchievementNotificationService {
    /// Content currently shown (nil = hidden). Observed by the overlay window.
    private(set) var activeCapsule: AchievementUnlockedContent?

    /// Counts feedback fires so tests can assert the guard without CoreHaptics.
    private(set) var hapticTrigger: Int = 0

    private let haptics: HapticsService
    private var capsuleDismissTask: Task<Void, Never>?

    /// Slightly longer than the karma flash: a badge title takes a beat more
    /// to read than "+N".
    let capsuleDuration: Duration = .milliseconds(3200)

    init(haptics: HapticsService = HapticsService()) {
        self.haptics = haptics
    }

    func notifyUnlocked(count: Int, title: String?, karmaTotal: Int) {
        guard count > 0 else { return }

        // Reuses the karma-earned haptic: same reward register, and a second
        // bespoke waveform for a moment that often fires alongside the karma
        // flash would double-buzz. One shared pattern keeps the pair coherent.
        hapticTrigger &+= 1
        haptics.playKarmaEarned()

        presentCapsule(AchievementUnlockedContent(count: count, title: title, karmaTotal: karmaTotal))
    }

    private func presentCapsule(_ content: AchievementUnlockedContent) {
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
