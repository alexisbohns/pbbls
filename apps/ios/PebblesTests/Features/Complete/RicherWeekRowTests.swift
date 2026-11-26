import Foundation
import Testing
@testable import Pebbles

@Suite("RicherWeekRow")
struct RicherWeekRowTests {
    @Test("seven days from Monday with the pebble's day marked")
    func days() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        // Wednesday 4 November 2026, 10:00 Paris.
        let wednesday = Date(timeIntervalSince1970: 1_793_782_800)
        let days = RicherWeekRow.days(around: wednesday, calendar: calendar)
        #expect(days.count == 7)
        #expect(days.map(\.isMarked) == [false, false, true, false, false, false, false])
        #expect(calendar.component(.weekday, from: days[0].date) == 2)
    }

    private static var paris: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    @Test("the marked day's index is where the stone lands: Wednesday is 2")
    func markedIndexWednesday() {
        // Wednesday 4 November 2026, 10:00 Paris.
        let wednesday = Date(timeIntervalSince1970: 1_793_782_800)
        #expect(RicherWeekRow.markedIndex(around: wednesday, calendar: Self.paris) == 2)
    }

    @Test("a Sunday closes the week: its index is 6")
    func markedIndexSunday() {
        // Sunday 8 November 2026, 10:00 Paris.
        let sunday = Date(timeIntervalSince1970: 1_793_782_800 + 4 * 86_400)
        #expect(RicherWeekRow.markedIndex(around: sunday, calendar: Self.paris) == 6)
    }

    @Test("a Monday opens the week: its index is 0")
    func markedIndexMonday() {
        // Monday 2 November 2026, 10:00 Paris.
        let monday = Date(timeIntervalSince1970: 1_793_782_800 - 2 * 86_400)
        #expect(RicherWeekRow.markedIndex(around: monday, calendar: Self.paris) == 0)
    }
}
