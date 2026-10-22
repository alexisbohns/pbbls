import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/spine-dates.test.ts at a04aeec5, with the
// same `\s` tolerance on the range's dash. TZ is Europe/Paris.
@Suite("SpineDates (web spine-dates.test.ts)")
struct SpineDatesTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    static let monday = V.local(2026, 9, 5)
    static let sunday = V.local(2026, 9, 11)

    @Test("shortens the weekday, lowercase and without French's period")
    func shortWeekday() {
        #expect(SpineDates.shortWeekday(Self.monday, calendar: V.paris, locale: V.en) == "mon")
        #expect(SpineDates.shortWeekday(Self.monday, calendar: V.paris, locale: V.fr) == "lun")
    }

    /// The bare locale, as iOS uses `Locale.current`'s language: "en" writes the month first.
    @Test("writes a day out in full")
    func longDay() {
        #expect(SpineDates.longDay(Self.monday, calendar: V.paris, locale: V.en) == "Monday, October 5")
        #expect(SpineDates.longDay(Self.monday, calendar: V.paris, locale: V.fr) == "lundi 5 octobre")
    }

    @Test("spans a week")
    func dayRange() throws {
        let english = SpineDates.dayRange(Self.monday, Self.sunday, calendar: V.paris, locale: V.en)
        let french = SpineDates.dayRange(Self.monday, Self.sunday, calendar: V.paris, locale: V.fr)
        #expect(english.wholeMatch(of: try Regex(#"Monday, October 5\s–\sSunday, October 11"#)) != nil, "\(english)")
        #expect(french.wholeMatch(of: try Regex(#"lundi 5\s–\sdimanche 11 octobre"#)) != nil, "\(french)")
    }

    @Test("names the month and the weekday as the opening and the dots read them")
    func names() {
        #expect(SpineDates.month(Self.monday, calendar: V.paris, locale: V.en) == "October")
        #expect(SpineDates.month(Self.monday, calendar: V.paris, locale: V.fr) == "octobre")
        #expect(SpineDates.weekday(Self.sunday, calendar: V.paris, locale: V.fr) == "dimanche")
    }
}
