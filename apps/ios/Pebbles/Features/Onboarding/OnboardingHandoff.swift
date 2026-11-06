import Foundation
import Observation

/// A one-shot mailbox between the flow and Home (#1510 D9). Process memory only: dying in between
/// loses the hand-off, which is fine. Bound to its user (correction 10), so a session ending between
/// the two never opens B's composer with A's prompt.
@Observable
@MainActor
final class OnboardingHandoff {
    /// Home's `.task(id:)`: the flow has ended, with or without a note.
    private(set) var revision = 0
    @ObservationIgnored private var note: OnboardingHandoffNote?

    /// The unlock cards the flow held back (#1565); the hand-off's composer is where they come back.
    @ObservationIgnored weak var cards: AchievementNotificationService?

    /// The hand-off composer saved its first moment: a pebble made there gets its card behind the held ones.
    func composerSaved() { cards?.release() }

    /// The hand-off composer closed. A no-op when nothing is held, so a later Quick moment may call it.
    func composerClosed() { cards?.release() }

    func leave(_ value: OnboardingHandoffNote) {
        note = value
        revision += 1
    }

    /// The flow ended and no composer follows, because a parked invite took over. Home still wakes:
    /// what it loaded under the cover is stale either way.
    func dropNote() {
        note = nil
        revision += 1
    }

    /// The note left for `userId`, or nil; either way the mailbox is empty afterwards.
    func take(userId: String?) -> OnboardingHandoffNote? {
        let taken = note
        note = nil
        guard let userId, taken?.userId == userId else {
            // No composer will open, so nothing is left to hold the cards for.
            cards?.release()
            return nil
        }
        return taken
    }
}

extension OnboardingHandoffNote {
    /// The composer's first prompt, chosen by the first reason picked.
    var prompt: LocalizedStringResource {
        switch reason {
        case .noticeFeelings: return "What did you feel today?"
        case .keepPeople: return "Who were you with today?"
        case .hardSeason: return "What was hard today?"
        case .goodDays: return "What was good today?"
        case .keepRecord: return "What do you want to remember from today?"
        case .ownPatterns, .none: return "What happened today?"
        }
    }
}
