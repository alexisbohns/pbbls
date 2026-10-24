import Foundation
@testable import Pebbles

/// `PathWrapServicing` for `PathModel`'s tests (#1226): answers set per call
/// kind, the zones asked for recorded. Android: `FakePathWrapService`.
final class FakePathWrapService: PathWrapServicing, @unchecked Sendable {
    struct Failure: Error {}

    private let lock = NSLock()
    private var stripAnswer: Result<Strip, Error>
    private var asked: [TimeZone] = []

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

    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap {
        throw Failure()
    }

    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        throw Failure()
    }
}
