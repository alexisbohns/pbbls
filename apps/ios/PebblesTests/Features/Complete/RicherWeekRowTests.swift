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
}
