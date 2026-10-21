import Foundation
@testable import Pebbles

/// A web vector's moment (#1130 D10): string ids, so the Vitest files copy verbatim.
struct VectorMoment: SpineMoment, Equatable {
    let spineId: String
    let happenedAt: Date
    let isPebble: Bool
    let soulIds: [String]
}

/// The Vitest environment, pinned: Europe/Paris (`apps/web/vitest.config.ts`),
/// ISO weeks, the bare `en` and `fr` locales.
enum Vectors {
    static let paris: Calendar = {
        var calendar = Calendar(identifier: .iso8601)
        guard let zone = TimeZone(identifier: "Europe/Paris") else { preconditionFailure("no Europe/Paris") }
        calendar.timeZone = zone
        return calendar
    }()
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")

    static func at(_ iso: String) -> Date {
        guard let date = ISO8601Flexible.parse(iso) else { preconditionFailure("unparseable \(iso)") }
        return date
    }

    /// JavaScript's `new Date(year, monthIndex, day, …)` in Paris: the month is
    /// 0-based, exactly as the web files write it.
    static func local(
        _ year: Int, _ monthIndex: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0, _ second: Int = 0
    ) -> Date {
        let parts = DateComponents(
            year: year, month: monthIndex + 1, day: day, hour: hour, minute: minute, second: second
        )
        guard let date = paris.date(from: parts) else { preconditionFailure("no date for \(parts)") }
        return date
    }

    /// The web's `isoWeekStart`.
    static func weekStart(_ date: Date) -> Date {
        WeekRollBuilder.weekStart(for: date, calendar: paris)
    }

    static func pebble(_ id: String, _ iso: String, souls: [String] = []) -> VectorMoment {
        VectorMoment(spineId: id, happenedAt: at(iso), isPebble: true, soulIds: souls)
    }

    static func bare(_ id: String, _ iso: String) -> VectorMoment {
        VectorMoment(spineId: id, happenedAt: at(iso), isPebble: false, soulIds: [])
    }
}
