import Foundation
import Testing
@testable import Pebbles

/// #1382: `path_month_wrap` sends no weeks below one pebble, so Home lays the
/// month's columns out from `days_in_month` and `first_weekday` alone (web's
/// `monthColumns` does the same, #1384).
@Suite("Week columns of a month without weeks")
struct WeekColumnsEmptyMonthTests {
    static func month(_ month: CalendarMonth, days: Int, firstWeekday: Int) -> MonthWrap {
        MonthWrap(month: month, daysInMonth: days, firstWeekday: firstWeekday, pebbles: 0, people: 0, sections: [],
                  cover: nil, weeks: nil, milestones: nil, persons: nil, previousMonth: nil, gem: nil,
                  fineEmotions: nil, domains: nil, tone: nil, hours: nil)
    }

    @Test("November 2026 (Sunday the 1st, 30 days): six columns from 26 October")
    func november2026() {
        let columns = WeekColumn.columns(Self.month(CalendarMonth(year: 2026, month: 11), days: 30, firstWeekday: 6))
        #expect(columns.count == 6)
        #expect(columns.map(\.week.weekStart) == [
            CalendarDay(year: 2026, month: 10, day: 26), CalendarDay(year: 2026, month: 11, day: 2),
            CalendarDay(year: 2026, month: 11, day: 9), CalendarDay(year: 2026, month: 11, day: 16),
            CalendarDay(year: 2026, month: 11, day: 23), CalendarDay(year: 2026, month: 11, day: 30)
        ])
        let first = columns[0]
        #expect(first.days == Array(repeating: .outside, count: 6) + [.empty], "only Sunday the 1st is in the month")
        #expect(first.week.from == 0 && first.week.to == 0)
        let last = columns[5]
        #expect(last.days == [.empty] + Array(repeating: .outside, count: 6))
        #expect(last.week.from == 29 && last.week.to == 29)
        #expect(columns.allSatisfy { $0.week.pebbles == 0 && $0.week.soulIds.isEmpty })
    }

    struct Case: Sendable {
        let month: CalendarMonth
        let days: Int
        let firstWeekday: Int
    }

    @Test("a month without weeks covers every day exactly once, Monday first", arguments: [
        Case(month: CalendarMonth(year: 2026, month: 2), days: 28, firstWeekday: 6),
        Case(month: CalendarMonth(year: 2027, month: 2), days: 28, firstWeekday: 0),
        Case(month: CalendarMonth(year: 2026, month: 10), days: 31, firstWeekday: 3),
        Case(month: CalendarMonth(year: 2026, month: 12), days: 31, firstWeekday: 1)
    ])
    func coversTheMonth(_ test: Case) {
        let (month, days, firstWeekday) = (test.month, test.days, test.firstWeekday)
        let columns = WeekColumn.columns(Self.month(month, days: days, firstWeekday: firstWeekday))
        #expect(columns.count == (firstWeekday + days + 6) / 7)
        #expect(columns.flatMap(\.days).filter { $0 == .empty }.count == days)
        #expect(columns.first?.days.prefix(firstWeekday).allSatisfy { $0 == .outside } == true)
        #expect(columns.first?.week.weekStart.date(in: TimeZone(secondsFromGMT: 0)!).map {
            Calendar(identifier: .iso8601).component(.weekday, from: $0)
        } == 2, "a Monday")
        #expect(columns.allSatisfy { $0.shape.stones == 0 }, "every cairn is the dotted stone")
    }

    @Test("served weeks are used as they are")
    @MainActor
    func servedWeeksWin() throws {
        let october = try #require(SpineFixtureMonth.october)
        #expect(WeekColumn.columns(october).map(\.week) == october.weeks)
    }
}
