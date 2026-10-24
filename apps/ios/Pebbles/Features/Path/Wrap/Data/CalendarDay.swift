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
        guard components.isValidDate(in: Self.gregorian) else { return nil }
        self.init(year: year, month: month, day: day)
    }

    /// The day holding `date` in `calendar`'s zone.
    init(_ date: Date, calendar: Calendar) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 0, month: parts.month ?? 0, day: parts.day ?? 0)
    }

    /// The wire form, `YYYY-MM-DD`.
    var iso: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    /// Midnight of this day in `calendar`'s zone.
    func date(in calendar: Calendar) -> Date? {
        calendar.date(from: DateComponents(year: year, month: month, day: day))
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    private static let gregorian: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? calendar.timeZone
        return calendar
    }()
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
struct CalendarMonth: Hashable, Comparable, Sendable {
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
}

private extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
