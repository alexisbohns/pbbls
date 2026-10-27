import Foundation

/// A day as the wraps send it (#1226): `YYYY-MM-DD` in the reader's zone, with
/// no time and no offset. It never goes through a `Date` decoding strategy (the
/// cross-surface timestamp rule); a view asks for the `Date` in the Spine's
/// calendar when it needs one.
struct CalendarDay: Hashable, Comparable, Sendable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Nil unless `iso` is exactly `YYYY-MM-DD` and names a real day.
    init?(iso: String) {
        let parts = iso.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCIIDigit) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]) else { return nil }
        let components = DateComponents(year: year, month: month, day: day)
        guard components.isValidDate(in: Self.utcGregorian) else { return nil }
        self.init(year: year, month: month, day: day)
    }

    /// The day holding `date` in `zone`, on the Gregorian calendar the wire
    /// speaks, whatever calendar the reader uses.
    init(_ date: Date, in zone: TimeZone) {
        let parts = Self.gregorian(in: zone).dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 0, month: parts.month ?? 0, day: parts.day ?? 0)
    }

    /// The wire form, `YYYY-MM-DD`.
    var iso: String {
        String(format: "%04ld-%02ld-%02ld", year, month, day)
    }

    /// Midnight of this day in `zone`.
    func date(in zone: TimeZone) -> Date? {
        Self.gregorian(in: zone).date(from: DateComponents(year: year, month: month, day: day))
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    private static func gregorian(in zone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    private static let utcGregorian = gregorian(in: TimeZone(secondsFromGMT: 0) ?? .current)
}

extension CalendarDay: Codable {
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let day = CalendarDay(iso: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "not a YYYY-MM-DD day: \(raw)")
        }
        self = day
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }
}

/// A month of the Path, named by its year and month (the month page's key).
struct CalendarMonth: Hashable, Comparable, Sendable, Identifiable {
    var id: Self { self }
    let year: Int
    let month: Int

    init(year: Int, month: Int) {
        self.year = year
        self.month = month
    }

    init(_ day: CalendarDay) {
        self.init(year: day.year, month: day.month)
    }

    /// The 1st, the form `path_month_wrap` takes as `p_month`.
    var firstDay: CalendarDay {
        CalendarDay(year: year, month: month, day: 1)
    }

    static func < (lhs: CalendarMonth, rhs: CalendarMonth) -> Bool {
        (lhs.year, lhs.month) < (rhs.year, rhs.month)
    }

    /// The month's last day, on the Gregorian calendar the wire speaks.
    var lastDay: CalendarDay {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = TimeZone(secondsFromGMT: 0) ?? gregorian.timeZone
        let first = gregorian.date(from: DateComponents(year: year, month: month, day: 1)) ?? .distantPast
        let days = gregorian.range(of: .day, in: .month, for: first)?.count ?? 28
        return CalendarDay(year: year, month: month, day: days)
    }
}

private extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}

extension CalendarMonth {
    /// The month before.
    var previous: CalendarMonth {
        month == 1 ? CalendarMonth(year: year - 1, month: 12) : CalendarMonth(year: year, month: month - 1)
    }
}
