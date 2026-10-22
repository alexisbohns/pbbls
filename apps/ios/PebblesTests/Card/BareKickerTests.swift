import Foundation
import Testing
@testable import Pebbles

// The kicker of apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/BareMomentPageTest.kt
// at d53be9cf: the full day and the time, in the zone. The day strings were
// formatted with this exact style on 2026-10-21; the time is the locale's short
// time, compared through the same formatter so ICU's leading zero cannot differ.
@Suite("Bare kicker (Android BareMomentPageTest.kt)")
struct BareKickerTests {
    private func calendar(_ zone: String) throws -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: zone))
        return calendar
    }

    private let sunday = ISO8601Flexible.parse("2026-10-11T07:40:00Z")

    @Test("the kicker is the full day and the time, in the zone")
    func kicker() throws {
        let date = try #require(sunday)
        let paris = try calendar("Europe/Paris")
        let english = CardDateline.kicker(date, calendar: paris, locale: Locale(identifier: "en_GB"))
        #expect(english.day == "Sunday 11 October")
        #expect(english.time == CardDateline.time(date, calendar: paris, locale: Locale(identifier: "en_GB")))
        let french = CardDateline.kicker(date, calendar: paris, locale: Locale(identifier: "fr_FR"))
        #expect(french.day == "dimanche 11 octobre")
        #expect(french.time.contains("9:40"))
        let newYork = CardDateline.kicker(date, calendar: try calendar("America/New_York"),
                                          locale: Locale(identifier: "fr_FR"))
        #expect(newYork.time.contains("3:40"))
    }
}
