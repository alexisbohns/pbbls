import Foundation
import Testing
@testable import Pebbles

/// #1146 §Recto: the leaf's full dateline, the locale's full weekday, day,
/// month and year, then " · " and the short time, in the device's zone.
/// Expected strings were formatted with these exact styles on 2026-10-21.
@Suite("CardDateline")
struct CardDatelineTests {
    private func paris() throws -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "Europe/Paris"))
        return calendar
    }

    private let thursday = ISO8601Flexible.parse("2026-10-08T19:00:00Z")

    @Test("British English")
    func english() throws {
        let date = try #require(thursday)
        #expect(CardDateline.text(date, calendar: try paris(), locale: Locale(identifier: "en_GB"))
            == "Thursday, 8 October 2026 · 21:00")
    }

    @Test("French")
    func french() throws {
        let date = try #require(thursday)
        #expect(CardDateline.text(date, calendar: try paris(), locale: Locale(identifier: "fr_FR"))
            == "jeudi 8 octobre 2026 · 21:00")
    }

    @Test("the Spine's ISO 8601 calendar keeps the locale's order (ICU has no patterns for it)")
    func isoCalendar() throws {
        let date = try #require(thursday)
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = try #require(TimeZone(identifier: "Europe/Paris"))
        #expect(CardDateline.text(date, calendar: iso, locale: Locale(identifier: "en_GB"))
            == "Thursday, 8 October 2026 · 21:00")
        #expect(CardDateline.text(date, calendar: iso, locale: Locale(identifier: "fr_FR"))
            == "jeudi 8 octobre 2026 · 21:00")
    }

    @Test("the zone decides the day: the same instant in New York is the afternoon")
    func zone() throws {
        let date = try #require(thursday)
        var newYork = Calendar(identifier: .gregorian)
        newYork.timeZone = try #require(TimeZone(identifier: "America/New_York"))
        #expect(CardDateline.text(date, calendar: newYork, locale: Locale(identifier: "fr_FR"))
            == "jeudi 8 octobre 2026 · 15:00")
    }
}
