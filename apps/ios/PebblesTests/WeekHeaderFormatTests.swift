import Foundation
import Testing
@testable import Pebbles

@Suite("WeekHeaderView.formatRange")
struct WeekHeaderFormatTests {

    private var calendar: Calendar {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = TimeZone(identifier: "UTC")!
        return iso
    }

    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso)!
    }

    @Test("same-year focus → no year suffix (English locale)")
    func sameYearEN() {
        let weekStart = date("2026-05-04T00:00:00Z")          // May 4 2026
        let today     = date("2026-05-10T12:00:00Z")          // Sun May 10 2026
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "en_US")
        )
        // Expected: "May 4 · May 10"
        #expect(label.contains("May 4"))
        #expect(label.contains("May 10"))
        #expect(!label.contains("2026"))
    }

    @Test("cross-year focus → year suffix appears (English locale)")
    func crossYearEN() {
        let weekStart = date("1990-01-29T00:00:00Z")           // ISO week 5 of 1990
        let today     = date("2026-05-10T12:00:00Z")
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "en_US")
        )
        // Expected something like "January 29 · February 4 · 1990"
        #expect(label.contains("1990"))
    }

    @Test("French locale renders month names in French")
    func frenchLocale() {
        let weekStart = date("2026-05-04T00:00:00Z")
        let today     = date("2026-05-10T12:00:00Z")
        let label = WeekHeaderView.formatRange(
            weekStart: weekStart, today: today,
            calendar: calendar, locale: Locale(identifier: "fr_FR")
        )
        // FR formats day before month: "4 mai · 10 mai"
        #expect(label.lowercased().contains("mai"))
    }
}
