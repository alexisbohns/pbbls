import Foundation
import Testing
@testable import Pebbles

/// The sheets' words (#1306), English and French, word for word Android's.
@Suite("Achievements sheet copy")
struct AchievementsSheetCopyTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    @Test("titles, kinds and counts")
    func labels() {
        #expect(AchievementsCopy.sheet("sixFirsts", locale: Self.fr) == "Les six premières fois")
        #expect(AchievementsCopy.sheet("firstFive", locale: Self.fr) == "Les cinq premières fois")
        #expect(AchievementsCopy.firstFiveTitle(.collection, locale: Self.en) == "First collection")
        #expect(AchievementsCopy.firstFiveTitle(nil, locale: Self.fr) == "Une première fois")
        #expect(AchievementsCopy.kind(.lowlight, locale: Self.fr) == "Creux")
        #expect(AchievementsCopy.kind(.large, locale: Self.en) == "Large")
        #expect(AchievementsCopy.kind(nil, locale: Self.en) == "A first")
        #expect(AchievementsCopy.ofWords(5, 5, locale: Self.en) == "five of five")
        #expect(AchievementsCopy.ofWords(1, 5, locale: Self.fr) == "un sur cinq")
        #expect(AchievementsCopy.countOf(14, 18, locale: Self.fr) == "14 sur 18")
        #expect(AchievementsCopy.registerNumber(3, locale: Self.en) == "No. 03")
        #expect(AchievementsCopy.registerNumber(14, locale: Self.fr) == "N° 14")
        #expect(AchievementsCopy.toGo(113, locale: Self.en) == "113 to go")
        #expect(AchievementsCopy.toGo(113, locale: Self.fr) == "encore 113")
        #expect(AchievementsCopy.thenEvery(500, locale: Self.en) == "then every 500")
        #expect(AchievementsCopy.firstYear(2026, locale: Self.fr) == "Aucune pour l'instant. 2026 est ta première année ici.")
        #expect(AchievementsCopy.momentName(nil, locale: Self.en) == "Untitled")
        #expect(AchievementsCopy.momentName(nil, locale: Self.fr) == "Sans titre")
        #expect(AchievementsCopy.sheet("unknownFamily", locale: Self.fr) == "Autres émotions")
    }

    @Test("a register date is the short day and month of a served day")
    func dates() {
        #expect(AchievementsCopy.day(CalendarDay(year: 2026, month: 5, day: 30), calendar: Self.paris,
                                     dateLocale: Locale(identifier: "en_US")) == "May 30")
        #expect(AchievementsCopy.day(CalendarDay(year: 2026, month: 3, day: 2), calendar: Self.paris,
                                     dateLocale: Locale(identifier: "fr_FR")) == "2 mars")
    }

    @Test("a row reads its parts and the new mark; the not yet read as one")
    func readAloud() {
        #expect(AchievementsCopy.row(["No. 14", "Events", "Flag march with Nadia", "Oct 1"], isNew: true, locale: Self.en)
            == "No. 14, Events, Flag march with Nadia, Oct 1, new since you last looked")
        #expect(AchievementsCopy.row(["250", nil, "113 to go"], locale: Self.fr) == "250, 113 to go")
        #expect(AchievementsCopy.notYet(["Anxious", "Bitter"], locale: Self.fr) == "Pas encore : Anxious, Bitter")
    }

    @Test("a month's row reads the served counts, each reached where the server says so")
    func monthRow() throws {
        let year = try #require(try AchievementsFixtures.payload("design-fixture").model.years.first)
        #expect(AchievementsCopy.monthRow(try #require(year.month(11)), locale: Self.en)
            == "November. Days with a moment: 2. With a pebble: 2. No full week yet.")
        #expect(AchievementsCopy.monthRow(try #require(year.month(8)), locale: Self.en)
            == "August. Days with a moment: 19, reached. With a pebble: 17, reached. Full weeks filled: 4 of 4, reached.")
        #expect(AchievementsCopy.monthRow(try #require(year.month(8)), locale: Self.fr)
            == "Août. Jours avec un moment : 19, atteint. Avec un galet : 17, atteint. Semaines complètes remplies : 4 sur 4, atteint.")
    }
}
