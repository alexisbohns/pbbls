import Foundation
@testable import Pebbles

/// `AchievementsPageServicing` for `AchievementsModel`'s tests (#1306). The page
/// answers are consumed in order, the last one repeating; every call is logged.
/// One read can be held (`hold(read:)`) until the test releases it, so a slow
/// read can land after a later one.
final class FakeAchievementsPageService: AchievementsPageServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var answers: [Result<AchievementsPage, Error>]
    private let keptPage: AchievementsPage?
    private var pullAnswer: Result<CeremonyPull, Error> = .failure(Failure())
    private var log: [String] = []
    private var seenCards: [AchievementCard] = []
    private var reads = 0
    private var heldRead: Int?
    private var held: CheckedContinuation<Void, Never>?

    init(kept: AchievementsPage? = nil, answers: [Result<AchievementsPage, Error>] = []) {
        keptPage = kept
        self.answers = answers
    }

    var calls: [String] { lock.withLock { log } }
    /// The zone each read, kept-copy read and pull was asked in, in order.
    var zones: [String] { lock.withLock { askedZones } }
    private var askedZones: [String] = []
    var seen: [AchievementCard] { lock.withLock { seenCards } }

    func record(_ entry: String) {
        lock.withLock { log.append(entry) }
    }

    func answer(pull: Result<CeremonyPull, Error>) {
        lock.withLock { pullAnswer = pull }
    }

    /// Holds the read with this index (0 is the first) until `release()`.
    func hold(read index: Int) {
        lock.withLock { heldRead = index }
    }

    /// Whether the held read is waiting.
    var isHolding: Bool { lock.withLock { held != nil } }

    func release() {
        let waiting = lock.withLock { () -> CheckedContinuation<Void, Never>? in
            defer { held = nil; heldRead = nil }
            return held
        }
        waiting?.resume()
    }

    func page(zone: TimeZone) async throws -> AchievementsPage {
        let (index, next) = lock.withLock { () -> (Int, Result<AchievementsPage, Error>) in
            log.append("read")
            askedZones.append(zone.identifier)
            defer { reads += 1 }
            let next = answers.count > 1 ? answers.removeFirst() : (answers.first ?? .failure(Failure()))
            return (reads, next)
        }
        if index == lock.withLock({ heldRead }) {
            await withCheckedContinuation { continuation in
                lock.withLock { held = continuation }
            }
        }
        return try next.get()
    }

    func cachedPage(zone: TimeZone) async -> AchievementsPage? {
        lock.withLock {
            log.append("kept")
            askedZones.append(zone.identifier)
            return keptPage
        }
    }

    /// When the kept copy was kept, as `keptAt` answers it.
    var keptAtDate: Date?

    func keptAt(zone: TimeZone) async -> Date? {
        lock.withLock { keptAtDate }
    }

    func markStale() {
        lock.withLock { log.append("stale") }
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
