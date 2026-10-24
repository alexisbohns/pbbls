import Foundation
import Testing
@testable import Pebbles

/// The month page's sentences (#1226), Android's `WrapCopyTest` and
/// `WrapCopyFrenchTest` month cases word for word.
@Suite("Wrap copy · month")
struct WrapCopyMonthTests {
    private let en = Vectors.en
    private let fr = Vectors.fr
    private let october = CalendarMonth(year: 2026, month: 10)
    private let september = CalendarMonth(year: 2026, month: 9)

    @Test("the month's cover, sections and sheet read in English")
    func english() {
        #expect(WrapCopy.monthKicker(october, locale: en) == "part the tenth")
        #expect(WrapCopy.monthName(october, locale: en) == "October")
        #expect(WrapCopy.colophon(inks: ["joy", "peace", "pride", "anger", "sadness", "fear"],
                                  papers: ["family", "identity", "friends"], locale: en)
                == "Printed in joy, peace & pride, with anger, sadness & fear, on family, identity & friends.")
        #expect(WrapCopy.weeksHeader(5, locale: en) == "five weeks")
        #expect(WrapCopy.weeksHint(october, locale: en) == "October by week")
        #expect(WrapCopy.milestonesHeader(1, locale: en) == "one milestone")
        #expect(WrapCopy.morePersons(6, locale: en) == "all six persons")
        #expect(WrapCopy.morePersons(4, locale: en) == "the persons, in detail")
        #expect(WrapCopy.range(october, from: 4, to: 10, locale: en) == "5–11 Oct")
        #expect(WrapCopy.dateline(CalendarDay(year: 2026, month: 10, day: 5), locale: en) == "Mon 5 Oct")
        #expect(WrapCopy.newSince(september, locale: en) == "new since September")
        #expect(WrapCopy.personsLead(top: [("Mara", 5), ("Nadia", 1)], fresh: ["Inès", "Jules"], previous: september,
                                     locale: en)
                == "Mara five times, Nadia once. Inès and Jules new since September.")
        #expect(WrapCopy.emotionsLead(emotions: 11, families: 6, locale: en)
                == "Eleven emotions in six families, weighed by intensity.")
        #expect(WrapCopy.domainsLead(domains: 7, top: "Family", topCount: 7, locale: en)
                == "Seven domains; family in seven pebbles.")
        #expect(WrapCopy.domainsLead(domains: 2, top: "Family", topCount: 1, locale: en)
                == "Two domains; family in one pebble.")
    }

    @Test("the month reads in French")
    func french() {
        #expect(WrapCopy.monthKicker(october, locale: fr) == "dixième partie")
        #expect(WrapCopy.monthName(october, locale: fr) == "Octobre")
        #expect(WrapCopy.weeksHeader(5, locale: fr) == "cinq semaines")
        #expect(WrapCopy.milestonesHeader(2, locale: fr) == "deux jalons")
        #expect(WrapCopy.morePersons(6, locale: fr) == "les six personnes")
        #expect(WrapCopy.colophon(inks: ["joie", "paix"], papers: ["famille"], locale: fr)
                == "Imprimé en joie & paix, sur famille.")
        // French months stay lowercase inside a sentence.
        #expect(WrapCopy.newSince(september, locale: fr) == "pas vu·e en septembre")
        #expect(WrapCopy.dateline(CalendarDay(year: 2026, month: 10, day: 5), locale: fr) == "lun 5 Oct")
    }

    @Test("the persons' lead counts in the feminine of fois, and says once as une fois")
    func frenchTimes() {
        #expect(WrapCopy.personsLead(top: [("Mara", 1), ("Bruno", 21)], fresh: ["Inès", "Jules"], previous: september,
                                     locale: fr)
                == "Mara une fois, Bruno vingt et une fois. Inès et Jules : pas vu·e·s en septembre.")
        #expect(WrapCopy.personsLead(top: [("Mara", 1), ("Bruno", 21)], fresh: [], previous: september, locale: en)
                == "Mara once, Bruno twenty-one times.")
    }

    @Test("a month numbers its 1st, its Mondays and its last day; its calendar starts on its weekday")
    @MainActor
    func numberedDays() throws {
        let wrap = try MonthPayloadFixture.october()
        #expect(wrap.numberedDays == [0, 4, 11, 18, 25, 30])
        #expect(wrap.dayMode(.calendar) == .calendar(leading: 3))
        #expect(wrap.dayMode(.circle) == .circle)
    }
}
