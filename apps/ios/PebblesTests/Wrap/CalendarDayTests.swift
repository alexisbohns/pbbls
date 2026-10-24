import Foundation
import Testing
@testable import Pebbles

@Suite("CalendarDay")
struct CalendarDayTests {
    @Test("parses YYYY-MM-DD and prints it back")
    func roundTrip() throws {
        let day = try #require(CalendarDay(iso: "2026-03-09"))
        #expect(day == CalendarDay(year: 2026, month: 3, day: 9))
        #expect(day.iso == "2026-03-09")
    }

    @Test("rejects anything but a real YYYY-MM-DD day", arguments: [
        "2026-02-30", "2026-13-01", "2026-1-01", "26-01-01", "2026-01-01T00:00:00Z", "", "2026-0a-01"
    ])
    func rejects(_ raw: String) {
        #expect(CalendarDay(iso: raw) == nil)
    }

    @Test("orders by year, month and day")
    func ordering() {
        #expect(CalendarDay(year: 2025, month: 12, day: 31) < CalendarDay(year: 2026, month: 1, day: 1))
        #expect(CalendarMonth(year: 2026, month: 9) < CalendarMonth(year: 2026, month: 10))
    }

    @Test("a day in a zone is the reader's day, not UTC's")
    func zonedDay() throws {
        var paris = Calendar(identifier: .iso8601)
        paris.timeZone = try #require(TimeZone(identifier: "Europe/Paris"))
        let lateUTC = try #require(ISO8601Flexible.parse("2026-10-31T23:30:00Z"))
        #expect(CalendarDay(lateUTC, calendar: paris) == CalendarDay(year: 2026, month: 11, day: 1))
        let midnight = try #require(CalendarDay(year: 2026, month: 11, day: 1).date(in: paris))
        #expect(CalendarDay(midnight, calendar: paris) == CalendarDay(year: 2026, month: 11, day: 1))
    }

    @Test("a month's first day is the p_month the server takes")
    func monthFirstDay() {
        #expect(CalendarMonth(year: 2026, month: 10).firstDay.iso == "2026-10-01")
    }
}
