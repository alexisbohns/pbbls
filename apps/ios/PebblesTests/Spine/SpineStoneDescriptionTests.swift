import Foundation
import Testing
@testable import Pebbles

/// #1130 A2–A3: the stone's and the slot's name, "Flag march with Nadia. Monday,
/// October 5, 10:15 AM. Large gem, Proud. With Nadia. Opens the pebble."
/// (Android SpineStoneDescriptionTest). Every soul is named, in the page's order.
@Suite("Spine stone description")
struct SpineStoneDescriptionTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    private func moment(_ name: String?) throws -> MomentSummary {
        try #require(F.moments.first { $0.name == name })
    }

    private func text(_ moment: MomentSummary, _ locale: Locale) -> String {
        SpineStoneDescription.text(moment, names: F.soulNames, order: [], calendar: F.calendar, locale: locale,
                                   dateLocale: locale)
    }

    private func when(_ moment: MomentSummary, _ locale: Locale) -> String {
        SpineDates.longDay(moment.happenedAt, calendar: F.calendar, locale: locale) + ", "
            + SpineDates.time(moment.happenedAt, calendar: F.calendar, locale: locale)
    }

    /// The emotion's catalogue name ("Pride" for the fixture's `proud`), as the stone reads it.
    private func emotion(_ moment: MomentSummary) -> String {
        moment.pebble?.emotion?.localizedName ?? ""
    }

    @Test("a pebble: name, when, stone kind, emotion, who, then what it opens")
    func pebble() throws {
        let flag = try moment("Flag march with Nadia")
        #expect(text(flag, Vectors.en) == "Flag march with Nadia. \(when(flag, Vectors.en)). Large gem, "
            + "\(emotion(flag)). With Nadia. Opens the pebble.")
    }

    @Test("a solitary pebble names nobody and leaves no double space")
    func solitary() throws {
        let rent = try moment("Rent went up again")
        #expect(text(rent, Vectors.fr) == "Rent went up again. \(when(rent, Vectors.fr)). Pierre de lave moyenne, "
            + "\(emotion(rent)). Ouvre le galet.")
    }

    @Test("a bare moment says it is not a pebble yet, an untitled one says untitled")
    func bare() throws {
        let bread = try moment("Bread from the corner bakery")
        #expect(text(bread, Vectors.en)
            == "Bread from the corner bakery. \(when(bread, Vectors.en)). Not a pebble yet. Opens the moment.")
        let photo = try #require(F.moments.first { $0.name == nil && $0.pebble == nil })
        #expect(text(photo, Vectors.en).hasPrefix("untitled. "))
    }

    @Test("several souls are listed in full, in the locale's list form")
    func several() throws {
        let forest = try moment("Forest walk, all of us")
        #expect(text(forest, Vectors.en).contains("With Mara, Elio, and Bruno."))
    }

    @Test("dates read the date locale, copy the app's language")
    func dateLocale() throws {
        let flag = try moment("Flag march with Nadia")
        let crossed = SpineStoneDescription.text(flag, names: F.soulNames, order: [], calendar: F.calendar,
                                                 locale: Vectors.en, dateLocale: Vectors.fr)
        #expect(crossed == "Flag march with Nadia. \(when(flag, Vectors.fr)). Large gem, "
            + "\(emotion(flag)). With Nadia. Opens the pebble.")
    }
}
