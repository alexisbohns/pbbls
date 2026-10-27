import Foundation

/// Home's months (spec §1.3), newest first: every month that holds a pebble,
/// folded or revealed.
struct HomeWraps: Hashable, Sendable {
    let months: [HomeMonth]
}

/// One month on Home: its pebble count, its cover, and whether the reader has
/// unfolded it yet (D5: `revealedAt` is the server's record of that).
struct HomeMonth: Hashable, Sendable, Identifiable {
    let month: CalendarMonth
    let count: Int
    let revealedAt: Date?
    let cover: MonthCover

    var id: CalendarMonth { month }
    var isFolded: Bool { revealedAt == nil }

    /// The month as the garland draws it: the Path's `MonthWrap` with only the
    /// cover, the counts and the calendar shape filled in.
    var asMonthWrap: MonthWrap {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = TimeZone(secondsFromGMT: 0) ?? gregorian.timeZone
        let first = gregorian.date(from: DateComponents(year: month.year, month: month.month, day: 1)) ?? .distantPast
        let length = gregorian.range(of: .day, in: .month, for: first)?.count ?? 30
        // Calendar weekday is 1 = Sunday; the wire is 0 = Monday.
        let weekday = (gregorian.component(.weekday, from: first) + 5) % 7
        return MonthWrap(
            month: month,
            daysInMonth: length,
            firstWeekday: weekday,
            pebbles: count,
            people: cover.soulsInOrder.count,
            sections: [],
            cover: cover,
            weeks: nil, milestones: nil, persons: nil, previousMonth: nil,
            gem: nil, fineEmotions: nil, domains: nil, tone: nil, hours: nil
        )
    }
}

/// What revealing a month answers: when the server recorded it (nil if it
/// recorded nothing).
struct HomeReveal: Hashable, Sendable {
    let month: CalendarMonth
    let revealedAt: Date?
}
