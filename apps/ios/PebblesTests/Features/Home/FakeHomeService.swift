import Foundation
@testable import Pebbles

/// `HomeServicing` for `HomeModel`'s tests: one answer per method, the calls recorded.
final class FakeHomeService: HomeServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var nameAnswer: @Sendable () async throws -> String?
    private var wrapsAnswer: Result<HomeWraps, Error> = .failure(Failure())
    private var revealAnswers: [CalendarMonth: Result<HomeReveal, Error>] = [:]
    private var guidesAnswer: Result<[Guide], Error> = .success([])
    private var guidesCallCount = 0
    private var monthAnswer: Result<MonthWrap, Error> = .failure(Failure())
    private var cachedMonthAnswer: MonthWrap?
    private var monthAsked: [CalendarMonth] = []
    private var wrapsAsked: [TimeZone] = []
    private var revealAsked: [CalendarMonth] = []

    init(answer: @escaping @Sendable () async throws -> String? = { nil }) {
        nameAnswer = answer
    }

    /// The name's answer; kept as a settable property so existing tests read the same.
    var answer: @Sendable () async throws -> String? {
        get { lock.withLock { nameAnswer } }
        set { lock.withLock { nameAnswer = newValue } }
    }

    func answer(wraps result: Result<HomeWraps, Error>) {
        lock.withLock { wrapsAnswer = result }
    }

    func answer(reveal month: CalendarMonth, _ result: Result<HomeReveal, Error>) {
        lock.withLock { revealAnswers[month] = result }
    }

    func answer(guides result: Result<[Guide], Error>) {
        lock.withLock { guidesAnswer = result }
    }

    func answer(month result: Result<MonthWrap, Error>, cached: MonthWrap? = nil) {
        lock.withLock {
            monthAnswer = result
            cachedMonthAnswer = cached
        }
    }

    var monthCalls: [CalendarMonth] { lock.withLock { monthAsked } }
    var guidesCalls: Int { lock.withLock { guidesCallCount } }
    var wrapsZones: [TimeZone] { lock.withLock { wrapsAsked } }
    var revealCalls: [CalendarMonth] { lock.withLock { revealAsked } }

    func displayName() async throws -> String? {
        try await answer()
    }

    func wraps(zone: TimeZone) async throws -> HomeWraps {
        try lock.withLock {
            wrapsAsked.append(zone)
            return try wrapsAnswer.get()
        }
    }

    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        try lock.withLock {
            revealAsked.append(month)
            return try (revealAnswers[month] ?? .failure(Failure())).get()
        }
    }

    func month(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        try lock.withLock {
            monthAsked.append(month)
            return try monthAnswer.get()
        }
    }

    func cachedMonth(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap? {
        lock.withLock { cachedMonthAnswer }
    }

    func guides() async throws -> [Guide] {
        try lock.withLock {
            guidesCallCount += 1
            return try guidesAnswer.get()
        }
    }
}
