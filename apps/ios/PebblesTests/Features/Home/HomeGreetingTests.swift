import Foundation
import Testing
@testable import Pebbles

@Suite("Home greeting") struct HomeGreetingTests {
    static let paris = TimeZone(identifier: "Europe/Paris")!
    static func date(_ s: String) -> Date {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; return f.date(from: s)!
    }
    static let en = Locale(identifier: "en"), fr = Locale(identifier: "fr")

    @Test func mondayMorningOpensTheWeek() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-02T08:10:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Morning, Alexis")
        #expect(g.line == "Hope you'll have a pleasant week!")
        #expect(g.dateline == "Monday 2 November")
    }
    @Test func sundayEveningAsksAboutTheWeek() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-08T20:45:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Good evening, Alexis")
        #expect(g.line == "How was your week?")
    }
    @Test func theBandsFallBackToTheirDefaults() {
        let afternoon = HomeGreeting.lines(at: Self.date("2026-11-04T15:30:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(afternoon.title == "Good afternoon, Alexis")
        #expect(afternoon.line == "Halfway through the day. Anything worth keeping?")
        let evening = HomeGreeting.lines(at: Self.date("2026-11-04T19:00:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(evening.line == "What stays with you from today?")
        let morning = HomeGreeting.lines(at: Self.date("2026-11-04T11:59:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(morning.line == "A new day to keep something from.")
    }
    @Test func mondayEveningAndSundayMorningAreOrdinary() {
        let mondayEvening = HomeGreeting.lines(at: Self.date("2026-11-02T20:00:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(mondayEvening.line == "What stays with you from today?")
        let sundayMorning = HomeGreeting.lines(at: Self.date("2026-11-08T09:00:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(sundayMorning.line == "A new day to keep something from.")
    }
    @Test func theZoneDecidesTheBand() {
        // 23:30 in Paris on Sunday is still Sunday evening; the same instant is Monday 08:30 in Tokyo.
        let tokyo = TimeZone(identifier: "Asia/Tokyo")!
        let instant = Self.date("2026-11-08T23:30:00+01:00")
        #expect(HomeGreeting.lines(at: instant, name: "A", zone: Self.paris, locale: Self.en).line == "How was your week?")
        #expect(HomeGreeting.lines(at: instant, name: "A", zone: tokyo, locale: Self.en).line == "Hope you'll have a pleasant week!")
    }
    @Test func frenchHasEveryLine() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-02T08:10:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.fr)
        #expect(g.title == "Bonjour, Alexis")
        #expect(g.line == "Belle semaine à toi !")
        #expect(g.dateline == "lundi 2 novembre")
    }
    @Test func anEmptyNameDropsTheComma() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-04T09:00:00+01:00"), name: "", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Morning")
    }

    @Test func whitespaceOnlyNameDropsTheComma() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-04T09:00:00+01:00"), name: "  \n ", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Morning")
    }
}
