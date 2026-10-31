import Foundation
import Testing
@testable import Pebbles

/// The page's words in English and French (#1306, spec §5): semantic values in,
/// sentences out, plurals and number words by the catalog and `NumberWords`.
@Suite("Achievements copy")
struct AchievementsCopyTests {
    static let en = Locale(identifier: "en")
    static let fr = Locale(identifier: "fr")
    static var paris: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return calendar
    }

    typealias Page = AchievementsPage

    static func ladder(count: Int, reached: [Int], next: Page.Next?) -> Page.Ladder {
        Page.Ladder(count: count, levels: reached.map { Page.Level(threshold: $0, on: nil, pebble: nil, isNew: false) },
                    next: next, ladder: [1, 10, 25, 50, 100, 250, 500, 1000], thenEvery: 500, since: nil)
    }

    @Test("the header: since a full date, then days, singular on the first")
    func header() {
        let day = CalendarDay(year: 2026, month: 3, day: 1)
        #expect(AchievementsCopy.since(day, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "since Sunday, March 1, 2026")
        #expect(AchievementsCopy.since(day, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "depuis le dimanche 1 mars 2026")
        #expect(AchievementsCopy.daysUnit(249, locale: Self.en) == "days")
        #expect(AchievementsCopy.daysUnit(1, locale: Self.en) == "day")
        #expect(AchievementsCopy.daysUnit(249, locale: Self.fr) == "jours")
        #expect(AchievementsCopy.daysUnit(1, locale: Self.fr) == "jour")
    }

    @Test("the cards' titles and hints")
    func cards() throws {
        #expect(AchievementCard.allCases.map { AchievementsCopy.title($0, locale: Self.en) }
            == ["Explorer", "Emotions", "Domains", "Pebbles", "The year"])
        #expect(AchievementCard.allCases.map { AchievementsCopy.title($0, locale: Self.fr) }
            == ["Explorateur", "Émotions", "Domaines", "Galets", "L'année"])
        let page = try AchievementsFixtures.payload("design-fixture").model
        let first = try #require(page.emotions?.since)
        #expect(AchievementsCopy.sinceHint(first, calendar: Self.paris, locale: Self.en, dateLocale: Self.en)
            == "since March 2")
        #expect(AchievementsCopy.sinceHint(first, calendar: Self.paris, locale: Self.fr, dateLocale: Self.fr)
            == "depuis le 2 mars")
        #expect(AchievementsCopy.domainsHint(locale: Self.fr) == "dans l'ordre d'arrivée")
        #expect(AchievementsCopy.yearHint(try #require(page.years.first), locale: Self.en) == "in progress")
    }

    @Test("a register date is the locale's short day and month")
    func shortDate() throws {
        let first = try #require(ISO8601Flexible.parse("2026-03-02T09:30:00Z"))
        #expect(SpineDates.shortDayMonth(first, calendar: Self.paris, locale: Self.en) == "Mar 2")
        #expect(SpineDates.shortDayMonth(first, calendar: Self.paris, locale: Self.fr) == "2 mars")
    }

    @Test("the coverage tier says what it means; an unknown tier says nothing")
    func coverage() {
        #expect(AchievementsCopy.coverage(.most, locale: Self.en)
            == "Your pebbles reach most parts of your life. Your insights have plenty to read.")
        #expect(AchievementsCopy.coverage(.most, locale: Self.fr)
            == "Tes galets touchent la plupart des parts de ta vie. Tes aperçus ont de quoi lire.")
        #expect(AchievementsCopy.coverage(.none, locale: Self.en)?.hasPrefix("Link your pebbles") == true)
        #expect(AchievementsCopy.coverage(.unknown, locale: Self.en) == nil)
    }

    @Test("the pebbles line counts the stones the server lists and names the one that rises")
    func pebblesLine() throws {
        let design = try #require(try AchievementsFixtures.payload("design-fixture").model.pebbles)
        #expect(AchievementsCopy.pebblesLine(design, locale: Self.en)
            == "137 pebbles. Five stones stand; the sixth rises toward 250.")
        #expect(AchievementsCopy.pebblesLine(design, locale: Self.fr)
            == "137 galets. Cinq pierres se dressent ; la sixième s'élève vers 250.")
        let one = Self.ladder(count: 1, reached: [1], next: Page.Next(threshold: 10, left: 9, progress: 0))
        #expect(AchievementsCopy.pebblesLine(one, locale: Self.en) == "1 pebble. One stone stands; the second rises toward 10.")
        #expect(AchievementsCopy.pebblesLine(one, locale: Self.fr)
            == "1 galet. Une pierre se dresse ; la deuxième s'élève vers 10.")
        let none = Self.ladder(count: 0, reached: [], next: Page.Next(threshold: 1, left: 1, progress: 0))
        #expect(AchievementsCopy.pebblesLine(none, locale: Self.en)
            == "0 pebbles. No stone stands yet; the first rises toward 1.")
        #expect(AchievementsCopy.pebblesLine(none, locale: Self.fr)
            == "0 galet. Aucune pierre ne se dresse encore ; la première s'élève vers 1.")
        let all = Self.ladder(count: 1137, reached: [1, 10, 25, 50, 100, 250, 500, 1000],
                              next: Page.Next(threshold: 1500, left: 363, progress: 0.274))
        #expect(AchievementsCopy.pebblesLine(all, locale: Self.en)
            == "1,137 pebbles. Every stone stands; the next mark is 1,500.")
        let n = { AchievementsCopy.number($0, locale: Self.fr) }
        #expect(AchievementsCopy.pebblesLine(all, locale: Self.fr)
            == "\(n(1137)) galets. Toutes les pierres se dressent ; le prochain cap est \(n(1500)).")
        #expect(AchievementsCopy.pebblesLead(design, locale: Self.en) == "137 pebbles kept.")
        #expect(AchievementsCopy.pebblesLead(design, locale: Self.fr) == "137 galets gardés.")
    }

    @Test("the year's next step, by what it needs, how many days are left and whether it can still be reached")
    func nextStep() {
        let moments = Page.NextStep(month: 11, needs: .moments, daysLeft: 12, reachable: true)
        #expect(AchievementsCopy.nextStep(moments, locale: Self.en)
            == "Twelve more days with a moment, one in each full week, bring November to the inner disc.")
        #expect(AchievementsCopy.nextStep(moments, locale: Self.fr)
            == "Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.")
        let one = Page.NextStep(month: 4, needs: .pebbles, daysLeft: 1, reachable: true)
        #expect(AchievementsCopy.nextStep(one, locale: Self.en)
            == "One more day with a pebble, and one in each full week, bring April to the rim.")
        #expect(AchievementsCopy.nextStep(one, locale: Self.fr)
            == "Un jour de plus avec un galet, et un dans chaque semaine complète : avril atteindra le bord.")
        let keep = Page.NextStep(month: 4, needs: .pebbles, daysLeft: 0, reachable: true)
        #expect(AchievementsCopy.nextStep(keep, locale: Self.en) == "Keep a pebble in every full week and April reaches the rim.")
        let late = Page.NextStep(month: 11, needs: .moments, daysLeft: 3, reachable: false)
        #expect(AchievementsCopy.nextStep(late, locale: Self.en) == "November can't reach the inner disc this month.")
        #expect(AchievementsCopy.nextStep(late, locale: Self.fr)
            == "Novembre n'atteindra pas le disque intérieur ce mois-ci.")
        let rim = Page.NextStep(month: 4, needs: .pebbles, daysLeft: 9, reachable: false)
        #expect(AchievementsCopy.nextStep(rim, locale: Self.en) == "April can't reach the rim this month.")
        #expect(AchievementsCopy.nextStep(rim, locale: Self.fr) == "Avril n'atteindra pas le bord ce mois-ci.")
        #expect(AchievementsCopy.monthHeading(11, locale: Self.fr) == "Novembre")
        #expect(AchievementsCopy.monthInSentence(13, locale: Self.en) == "13")
    }

    @Test("a pulled ceremony's card names its stack, as the catalog does, and the karma it paid")
    func ceremony() {
        let card = AchievementsCopy.ceremonyCard(.emotions, karma: 40, locale: Self.en)
        #expect(card == AchievementMomentCard(id: "complete-emotions", title: "Every emotion",
                                              description: "Your pebbles have carried every feeling on the wheel.",
                                              karmaGranted: 40))
        let unpaid = AchievementsCopy.ceremonyCard(.domains, karma: nil, locale: Self.fr)
        #expect(unpaid.title == "Tous les domaines" && unpaid.karmaGranted == 0,
                "a pull confirmed only by the re-read plays without a karma line")
        #expect(AchievementsCopy.ceremonyTitle(.sixFirsts, locale: Self.fr) == "Les six premières fois")
        #expect(AchievementsCopy.ceremonySlug(.sixFirsts) == "complete-six-firsts")
    }
}
