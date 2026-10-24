import Foundation
@testable import Pebbles

/// `PathWrapServicing` for `PathModel`'s tests (#1226): answers set per call
/// kind, the zones asked for recorded. Android: `FakePathWrapService`.
final class FakePathWrapService: PathWrapServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var stripAnswer: Result<Strip, Error>
    private var asked: [TimeZone] = []
    private var weekAnswers: [CalendarDay: Result<WeekWrap, Error>] = [:]
    private var weekAsked: [CalendarDay] = []

    init(strip: Result<Strip, Error> = .failure(Failure())) {
        stripAnswer = strip
    }

    func answer(strip: Result<Strip, Error>) {
        lock.withLock { stripAnswer = strip }
    }

    var stripZones: [TimeZone] {
        lock.withLock { asked }
    }

    func strip(zone: TimeZone) async throws -> Strip {
        try lock.withLock {
            asked.append(zone)
            return try stripAnswer.get()
        }
    }

    func answer(week: CalendarDay, _ result: Result<WeekWrap, Error>) {
        lock.withLock { weekAnswers[week] = result }
    }

    /// The weeks asked for, in order.
    var weekCalls: [CalendarDay] {
        lock.withLock { weekAsked }
    }

    /// How long a week's answer takes; it is read when the call starts.
    var weekDelay: Duration? {
        get { lock.withLock { delay } }
        set { lock.withLock { delay = newValue } }
    }
    private var delay: Duration?

    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap {
        let (answer, wait) = lock.withLock { () -> (Result<WeekWrap, Error>, Duration?) in
            weekAsked.append(weekStart)
            return (weekAnswers[weekStart] ?? .failure(Failure()), delay)
        }
        if let wait { try await Task.sleep(for: wait) }
        return try answer.get()
    }

    private var monthAnswers: [CalendarMonth: Result<MonthWrap, Error>] = [:]
    private var monthAsked: [CalendarMonth] = []
    private var keptMonths: [CalendarMonth: MonthWrap] = [:]

    func answer(month: CalendarMonth, _ result: Result<MonthWrap, Error>) {
        lock.withLock { monthAnswers[month] = result }
    }

    func keep(month: CalendarMonth, _ wrap: MonthWrap) {
        lock.withLock { keptMonths[month] = wrap }
    }

    var monthCalls: [CalendarMonth] {
        lock.withLock { monthAsked }
    }

    /// How long a month's answer takes; it is read when the call starts.
    var monthDelay: Duration? {
        get { lock.withLock { monthWait } }
        set { lock.withLock { monthWait = newValue } }
    }
    private var monthWait: Duration?

    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        let (answer, wait) = lock.withLock { () -> (Result<MonthWrap, Error>, Duration?) in
            monthAsked.append(month)
            return (monthAnswers[month] ?? .failure(Failure()), monthWait)
        }
        if let wait { try await Task.sleep(for: wait) }
        return try answer.get()
    }

    // MARK: - The device's copies

    private var keptStrip: Strip?
    private var keptWeeks: [CalendarDay: WeekWrap] = [:]

    func keep(strip: Strip?) {
        lock.withLock { keptStrip = strip }
    }

    func keep(week: CalendarDay, _ wrap: WeekWrap) {
        lock.withLock { keptWeeks[week] = wrap }
    }

    func cachedStrip(zone: TimeZone) async -> Strip? {
        lock.withLock { keptStrip }
    }

    func cachedWeekWrap(_ weekStart: CalendarDay, zone: TimeZone) async -> WeekWrap? {
        lock.withLock { keptWeeks[weekStart] }
    }

    func cachedMonthWrap(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap? {
        lock.withLock { keptMonths[month] }
    }
}
