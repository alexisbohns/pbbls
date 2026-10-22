import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/week-opening.test.ts at a04aeec5, verbatim.
// The web's `previous: { number }` and `next: { number, ordinal, monday }` are
// WeekWords' flat `previousNumber`, `nextNumber`, `nextOrdinal`, `nextMonday`.
@Suite("WeekOpening (web week-opening.test.ts)")
struct WeekOpeningTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors
    static let w41 = V.weekStart(V.local(2026, 9, 5))

    private func words(_ weekStart: Date, _ locale: Locale) -> WeekWords {
        WeekOpening.weekWords(weekStart: weekStart, calendar: V.paris, locale: locale)
    }

    @Test("sets the brief's week in words")
    func english() {
        #expect(words(Self.w41, V.en) == WeekWords(
            number: 41, year: 2026, ordinal: "forty-first",
            title: .sameMonth(from: "fifth", to: "eleventh", month: "October"),
            previousNumber: 40, nextNumber: 42, nextOrdinal: "forty-second", nextMonday: "twelfth"
        ))
    }

    @Test("reads a French week as feminine and its days as cardinals")
    func french() {
        let french = words(Self.w41, V.fr)
        #expect(french.ordinal == "quarante et unième")
        #expect(french.title == .sameMonth(from: "cinq", to: "onze", month: "octobre"))
        #expect(french.nextNumber == 42)
        #expect(french.nextOrdinal == "quarante-deuxième")
        #expect(french.nextMonday == "douze")
    }

    @Test("names both months when the week crosses one")
    func crossMonth() {
        let french = words(V.weekStart(V.local(2026, 8, 28)), V.fr)
        #expect(french.title == .crossMonth(
            from: "vingt-huit", fromMonth: "septembre", to: "quatre", toMonth: "octobre"
        ))
        #expect(french.nextMonday == "cinq")
    }

    @Test("takes the ISO year and wraps the week numbers at the new year")
    func newYear() {
        let french = words(V.weekStart(V.local(2026, 11, 28)), V.fr)
        #expect(french.number == 53)
        #expect(french.year == 2026)
        #expect(french.previousNumber == 52)
        #expect(french.nextNumber == 1)
        #expect(french.nextOrdinal == "première")
        #expect(french.nextMonday == "quatre")
        let english = words(V.weekStart(V.local(2025, 11, 29)), V.en)
        #expect(english.number == 1)
        #expect(english.year == 2026)
        #expect(english.ordinal == "first")
    }

    @Test("says premier for the first of a month in French")
    func premier() {
        guard case let .sameMonth(from, to, _) = words(V.weekStart(V.local(2027, 2, 1)), V.fr).title else {
            Issue.record("1 March 2027 to 7 March 2027 is one month")
            return
        }
        #expect(from == "premier")
        #expect(to == "sept")
    }

    @Test("counts ISO weeks even when the caller passes a Gregorian calendar")
    func gregorianCaller() {
        // US rules put Monday 28 December 2026 in week 1 of 2027; ISO keeps it in 53 of 2026.
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = V.paris.timeZone
        gregorian.firstWeekday = 1
        gregorian.minimumDaysInFirstWeek = 1
        let monday = V.weekStart(V.local(2026, 11, 28))
        let words = WeekOpening.weekWords(weekStart: monday, calendar: gregorian, locale: V.fr)
        #expect(words.number == 53)
        #expect(words.year == 2026)
        #expect(words.previousNumber == 52)
        #expect(words.nextNumber == 1)
    }

    @Test("agrees in gender in French")
    func summaryWords() {
        #expect(WeekOpening.summaryWords(pebbles: 1, people: 1, locale: V.fr) == ("un", "une"))
        #expect(WeekOpening.summaryWords(pebbles: 21, people: 31, locale: V.fr) == ("vingt et un", "trente et une"))
        #expect(WeekOpening.summaryWords(pebbles: 11, people: 5, locale: V.en) == ("eleven", "five"))
    }

    @Test("capitalises the first letter only")
    func sentenceCase() {
        #expect(WeekOpening.sentenceCase("eleven pebbles, five people.", locale: V.en)
            == "Eleven pebbles, five people.")
        #expect(WeekOpening.sentenceCase("onze galets.", locale: V.fr) == "Onze galets.")
    }

    @Test("runs the weekday letters Monday first, lowercase")
    func letters() {
        #expect(WeekOpening.weekdayLetters(weekStart: Self.w41, calendar: V.paris, locale: V.en)
            == ["m", "t", "w", "t", "f", "s", "s"])
        #expect(WeekOpening.weekdayLetters(weekStart: Self.w41, calendar: V.paris, locale: V.fr)
            == ["l", "m", "m", "j", "v", "s", "d"])
    }

    @Test("names the marked days with their dates")
    func dotDays() {
        let english = WeekOpening.dotDays([true, true, false, true, false, true, false], weekStart: Self.w41,
                                          calendar: V.paris, locale: V.en)
        #expect(english == (4, "Monday 5, Tuesday 6, Thursday 8, and Saturday 10"))
        let french = WeekOpening.dotDays([false, false, false, false, false, false, true], weekStart: Self.w41,
                                         calendar: V.paris, locale: V.fr)
        #expect(french == (1, "dimanche 11"))
    }
}
