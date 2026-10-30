import Foundation
import Testing
@testable import Pebbles

/// VoiceOver labels assembled from the payload (#1306, spec §5): each card reads
/// as the server's values, the disc as the server's lists, never as a drawing.
@Suite("Achievements accessibility labels")
struct AchievementsA11yTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }
    static func newUser() throws -> AchievementsPage { try AchievementsFixtures.payload("new-user").model }

    @Test("the header: the days kept, then since when")
    func header() {
        let since = CalendarDay(year: 2026, month: 3, day: 1)
        #expect(AchievementsA11y.header(daysKept: 249, since: since, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "249 days, since Sunday, March 1, 2026")
        #expect(AchievementsA11y.header(daysKept: 249, since: since, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "249 jours, depuis le dimanche 1 mars 2026")
    }

    @Test("Explorer: each ring done or how far, then what is new")
    func explorer() throws {
        let page = try Self.design()
        let explorer = try #require(page.explorer)
        #expect(AchievementsA11y.explorer(explorer, emotions: page.emotions, domains: page.domains, locale: Self.en)
            == "Explorer: the first five, done; the six firsts, done; 25 pebbles, done; domains, 14 of 18; emotions, 23 of 38. 3 new.")
        #expect(AchievementsA11y.explorer(explorer, emotions: page.emotions, domains: page.domains, locale: Self.fr)
            == "Explorateur : les cinq premières fois, c'est fait ; les six premières fois, c'est fait ; 25 galets, c'est fait ; domaines, 14 sur 18 ; émotions, 23 sur 38. 3 nouveautés.")
        let fresh = try Self.newUser()
        #expect(AchievementsA11y.explorer(try #require(fresh.explorer), emotions: fresh.emotions, domains: fresh.domains,
                                          locale: Self.en)
            == "Explorer: the first five, 1 of 5; the six firsts, 0 of 6; 25 pebbles, 0 of 25; domains, 0 of 18; emotions, 0 of 38.")
    }

    @Test("Emotions and Domains: the count, the coverage's meaning, what is new")
    func emotionsAndDomains() throws {
        let page = try Self.design()
        #expect(AchievementsA11y.emotions(try #require(page.emotions), locale: Self.en) == "Emotions, 23 of 38. 2 new.")
        #expect(AchievementsA11y.emotions(try #require(page.emotions), locale: Self.fr) == "Émotions, 23 sur 38. 2 nouveautés.")
        #expect(AchievementsA11y.domains(try #require(page.domains), locale: Self.en)
            == "Domains, 14 of 18. Your pebbles reach most parts of your life. Your insights have plenty to read. 1 new.")
    }

    @Test("Pebbles: the line, then the glyph stacks")
    func pebbles() throws {
        let page = try Self.design()
        #expect(AchievementsA11y.pebbles(try #require(page.pebbles), carved: page.glyphsCarved, sales: page.glyphSales,
                                         locale: Self.en)
            == "Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250. Glyphs carved: 12. Glyph sales: 3.")
        let fresh = try Self.newUser()
        #expect(AchievementsA11y.pebbles(try #require(fresh.pebbles), carved: fresh.glyphsCarved, sales: fresh.glyphSales,
                                         locale: Self.fr)
            == "Galets. 0 galet. Aucune pierre ne se dresse encore ; la première s'élève vers 1. Glyphes gravés : 0. Ventes de glyphes : 0.")
    }

    @Test("the year reads as the server's lists, then the next step")
    func year() throws {
        let year = try #require(try Self.design().years.first)
        #expect(AchievementsA11y.year(year, locale: Self.en)
            == "The year 2026. On the rim: August. On the inner disc: March, April, May, June, July, September, and October. "
            + "On their way: November. Twelve more days with a moment, one in each full week, bring November to the inner disc.")
        #expect(AchievementsA11y.year(year, locale: Self.fr)
            == "L'année 2026. Au bord : août. Sur le disque intérieur : mars, avril, mai, juin, juillet, septembre et octobre. "
            + "En chemin : novembre. Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.")
        let quiet = try #require(try Self.newUser().years.first)
        #expect(AchievementsA11y.year(quiet, locale: Self.en)
            == "The year 2026. On the rim: none. On the inner disc: none. On their way: none. "
            + "October can't reach the inner disc this month.")
        #expect(AchievementsA11y.disc(quiet, locale: Self.en)
            == "The year 2026. On the rim: none. On the inner disc: none. On their way: none.")
    }

    @Test("a ready mark says which ceremony it opens")
    func ready() {
        #expect(AchievementsA11y.ready(.sixFirsts, locale: Self.en) == "The six firsts: the ceremony is ready.")
        #expect(AchievementsA11y.ready(.emotions, locale: Self.fr) == "Toutes les émotions : la cérémonie est prête.")
        #expect(AchievementsCopy.ready(locale: Self.fr) == "prêt")
        #expect(AchievementsCopy.glyphsCarved(locale: Self.en) == "Glyphs carved")
    }
}
