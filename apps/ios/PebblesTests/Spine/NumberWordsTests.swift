import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/i18n/number-words.test.ts at a04aeec5, verbatim. The
// web's `cardinalWords(2.5)` case has no Swift counterpart: `cardinal` takes an Int.
@Suite("NumberWords (web number-words.test.ts)")
struct NumberWordsTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors

    @Test(arguments: [
        (0, "zero", "zéro"), (1, "one", "un"), (12, "twelve", "douze"), (17, "seventeen", "dix-sept"),
        (20, "twenty", "vingt"), (21, "twenty-one", "vingt et un"), (22, "twenty-two", "vingt-deux"),
        (31, "thirty-one", "trente et un"), (41, "forty-one", "quarante et un"),
        (53, "fifty-three", "cinquante-trois"), (60, "sixty", "soixante")
    ])
    // swiftlint:disable:next large_tuple
    func cardinal(_ vector: (Int, String, String)) {
        #expect(NumberWords.cardinal(vector.0, locale: V.en) == vector.1)
        #expect(NumberWords.cardinal(vector.0, locale: V.fr) == vector.2)
    }

    @Test("agrees with a feminine noun in French on the ones")
    func feminine() {
        #expect(NumberWords.cardinal(1, locale: V.fr, gender: .feminine) == "une")
        #expect(NumberWords.cardinal(21, locale: V.fr, gender: .feminine) == "vingt et une")
        #expect(NumberWords.cardinal(51, locale: V.fr, gender: .feminine) == "cinquante et une")
        #expect(NumberWords.cardinal(2, locale: V.fr, gender: .feminine) == "deux")
        #expect(NumberWords.cardinal(1, locale: V.en, gender: .feminine) == "one")
    }

    @Test("falls back to digits outside 0–60")
    func cardinalFallback() {
        #expect(NumberWords.cardinal(61, locale: V.en) == "61")
        #expect(NumberWords.cardinal(-1, locale: V.fr) == "-1")
    }

    @Test(arguments: [
        (1, "first", "premier"), (2, "second", "deuxième"), (5, "fifth", "cinquième"), (9, "ninth", "neuvième"),
        (11, "eleventh", "onzième"), (12, "twelfth", "douzième"), (20, "twentieth", "vingtième"),
        (21, "twenty-first", "vingt et unième"), (25, "twenty-fifth", "vingt-cinquième"),
        (31, "thirty-first", "trente et unième"), (39, "thirty-ninth", "trente-neuvième"),
        (40, "fortieth", "quarantième"), (41, "forty-first", "quarante et unième"),
        (44, "forty-fourth", "quarante-quatrième"), (53, "fifty-third", "cinquante-troisième")
    ])
    // swiftlint:disable:next large_tuple
    func ordinal(_ vector: (Int, String, String)) {
        #expect(NumberWords.ordinal(vector.0, locale: V.en) == vector.1)
        #expect(NumberWords.ordinal(vector.0, locale: V.fr) == vector.2)
    }

    @Test("sets the first week as 'la première semaine'")
    func firstWeek() {
        #expect(NumberWords.ordinal(1, locale: V.fr, gender: .feminine) == "première")
        #expect(NumberWords.ordinal(21, locale: V.fr, gender: .feminine) == "vingt et unième")
    }

    @Test("falls back to digits outside 1–53")
    func ordinalFallback() {
        #expect(NumberWords.ordinal(0, locale: V.en) == "0")
        #expect(NumberWords.ordinal(54, locale: V.fr) == "54")
    }

    @Test("reads English days as ordinals and French days as cardinals, with 'premier' for the 1st")
    func dayOfMonth() {
        #expect(NumberWords.dayOfMonth(1, locale: V.en) == "first")
        #expect(NumberWords.dayOfMonth(21, locale: V.en) == "twenty-first")
        #expect(NumberWords.dayOfMonth(31, locale: V.en) == "thirty-first")
        #expect(NumberWords.dayOfMonth(1, locale: V.fr) == "premier")
        #expect(NumberWords.dayOfMonth(5, locale: V.fr) == "cinq")
        #expect(NumberWords.dayOfMonth(21, locale: V.fr) == "vingt et un")
        #expect(NumberWords.dayOfMonth(31, locale: V.fr) == "trente et un")
        #expect(NumberWords.dayOfMonth(32, locale: V.en) == "32")
    }
}
