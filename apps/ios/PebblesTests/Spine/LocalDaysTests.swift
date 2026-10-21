import Foundation
import Testing
@testable import Pebbles

// The DST vectors of apps/web/lib/utils/spine/persons-of-week.test.ts at a04aeec5
// (local-day.ts has no test file of its own). Europe/Paris: summer time ends
// Sunday 25 Oct 2026 at 03:00 and starts Sunday 29 Mar 2026 at 02:00.
@Suite("LocalDays (web persons-of-week DST vectors)")
struct LocalDaysTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    static let dstWeek = V.weekStart(V.local(2026, 9, 19))
    static let springWeek = V.weekStart(V.local(2026, 2, 23))

    private func index(_ iso: String, in week: Date) -> Int {
        LocalDays.dayIndexInWeek(weekStart: week, V.at(iso), calendar: V.paris)
    }

    @Test("Monday 10:00 summer time is day 0, Thursday 23:30 is still Thursday")
    func summer() {
        #expect(index("2026-10-19T08:00:00Z", in: Self.dstWeek) == 0)
        #expect(index("2026-10-22T21:30:00Z", in: Self.dstWeek) == 3)
    }

    @Test("Sunday 23:59 on the day the clocks go back is Sunday, whole-second or fractional")
    func autumnSunday() {
        #expect(index("2026-10-25T22:59:00Z", in: Self.dstWeek) == 6)
        #expect(index("2026-10-25T22:59:59.123456+00:00", in: Self.dstWeek) == 6)
    }

    @Test("the week before and after fall outside 0…6")
    func outside() {
        #expect(index("2026-10-18T21:59:00Z", in: Self.dstWeek) == -1)
        #expect(index("2026-10-25T23:00:00Z", in: Self.dstWeek) == 7)
    }

    @Test("a week that loses an hour keeps its Sunday and not the next Monday")
    func spring() {
        #expect(index("2026-03-29T21:59:00Z", in: Self.springWeek) == 6)
        #expect(index("2026-03-29T22:30:00Z", in: Self.springWeek) == 7)
    }
}
