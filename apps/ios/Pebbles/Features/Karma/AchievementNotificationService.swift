import Foundation

/// One card of an unlock moment: a badge that just unlocked, with the karma it
/// actually paid (as reported by `check_achievements()`, never the catalog's
/// current value).
struct AchievementMomentCard: Equatable, Sendable, Identifiable {
    /// The badge slug — stable, and the fallback headline if copy is missing.
    let id: String
    let title: String
    let description: String?
    let karmaGranted: Int
}

/// Explicit-fire entry point for the achievement unlock moment (D13).
///
/// Sibling of `KarmaNotificationService`, but a different shape of celebration:
/// where karma flashes a pastille that times out, an unlock opens a chained
/// card per badge that the user taps through. Both are hosted by the same
/// overlay window, which floats above presented sheets — the composer dismisses
/// itself on the very success that fires this, so presenting from the view tree
/// would race that dismissal.
///
/// Only the mutation path celebrates: the screen-open call in `AchievementsView`
/// is the retroactive grant and can return a veteran's whole history at once,
/// so it renders in the grid instead of chaining twenty cards.
@Observable
@MainActor
final class AchievementNotificationService {
    /// The queue being celebrated (empty = nothing showing).
    private(set) var cards: [AchievementMomentCard] = []
    /// Index of the card on screen.
    private(set) var index: Int = 0

    /// Counts feedback fires so tests can assert the guard without CoreHaptics.
    private(set) var hapticTrigger: Int = 0

    /// The onboarding is up (#1565): `currentCard` is nil and `present` only queues.
    private(set) var isHeld = false
    /// A held queue was just released: a card fired next (the first pebble's) chains behind it
    /// instead of replacing it. Ends with the queue.
    private var chainsNext = false

    private let haptics: HapticsService

    init(haptics: HapticsService = HapticsService()) {
        self.haptics = haptics
    }

    /// The card on screen, or nil when the moment is idle.
    var currentCard: AchievementMomentCard? {
        guard !isHeld, index >= 0, index < cards.count else { return nil }
        return cards[index]
    }

    var isShowingLastCard: Bool {
        index + 1 >= cards.count
    }

    func present(cards: [AchievementMomentCard]) {
        guard !cards.isEmpty else { return }

        if isHeld {
            self.cards += cards
            return
        }
        if chainsNext, !self.cards.isEmpty {
            self.cards += cards
            return
        }

        // Reuses the karma-earned haptic: same reward register, and a second
        // bespoke waveform for a moment that often fires alongside the karma
        // flash would double-buzz.
        hapticTrigger &+= 1
        haptics.playKarmaEarned()

        self.cards = cards
        self.index = 0
    }

    /// Keeps the queue out of sight: the onboarding is up, and a card landing on top of it would
    /// take the flow over (#1565). Whoever holds must release on every way out, since a hold left on
    /// swallows every card in the app.
    func hold() {
        isHeld = true
    }

    /// Lets the queue show, if there is one. A no-op when nothing is held, so callers needn't ask.
    func release() {
        guard isHeld else { return }
        isHeld = false
        guard !cards.isEmpty else { return }
        chainsNext = true
        hapticTrigger &+= 1
        haptics.playKarmaEarned()
    }

    /// Signed out: nothing queued for this account may show for the next, and no hold outlives it.
    func reset() {
        isHeld = false
        dismiss()
    }

    /// Advances to the next card, ending the moment after the last one.
    func advance() {
        if isShowingLastCard {
            dismiss()
        } else {
            index += 1
        }
    }

    /// Ends the moment immediately — tapping outside or swiping down skips the
    /// rest of the queue. Dismissal is never blocking.
    func dismiss() {
        chainsNext = false
        cards = []
        index = 0
    }
}
