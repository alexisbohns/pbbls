import Foundation
@testable import Pebbles

/// `AchievementsPageServicing` for `AchievementsModel`'s tests (#1306). The page
/// answers are consumed in order, the last one repeating; every call is logged.
final class FakeAchievementsPageService: AchievementsPageServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var answers: [Result<AchievementsPage, Error>]
    private let keptPage: AchievementsPage?
    private var pullAnswer: Result<CeremonyPull, Error> = .failure(Failure())
    private var log: [String] = []
    private var seenCards: [AchievementCard] = []

    init(kept: AchievementsPage? = nil, answers: [Result<AchievementsPage, Error>] = []) {
        keptPage = kept
        self.answers = answers
    }

    var calls: [String] { lock.withLock { log } }
    var seen: [AchievementCard] { lock.withLock { seenCards } }

    func record(_ entry: String) {
        lock.withLock { log.append(entry) }
    }

    func answer(pull: Result<CeremonyPull, Error>) {
        lock.withLock { pullAnswer = pull }
    }

    func page(zone: TimeZone) async throws -> AchievementsPage {
        try lock.withLock {
            log.append("read")
            let next = answers.count > 1 ? answers.removeFirst() : (answers.first ?? .failure(Failure()))
            return try next.get()
        }
    }

    func cachedPage(zone: TimeZone) async -> AchievementsPage? {
        lock.withLock {
            log.append("kept")
            return keptPage
        }
    }

    func markSeen(_ card: AchievementCard) async throws {
        lock.withLock { seenCards.append(card) }
    }

    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull {
        try lock.withLock {
            log.append("pull \(stack.rawValue)")
            return try pullAnswer.get()
        }
    }
}
