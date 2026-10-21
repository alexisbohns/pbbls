import Foundation
import Testing
@testable import Pebbles

/// The port of apps/web/lib/seed/spine-fixture-week.ts takes every branch the
/// product takes (the web file's own list), and reads like the web's week.
@Suite("Spine fixture week")
struct SpineFixtureWeekTests {
    typealias F = SpineFixtureWeek

    private var week: SpineWeek<MomentSummary> {
        SpineWeekBuilder.spineWeek(weekStart: F.weekStart, moments: F.moments, today: F.today, calendar: F.calendar)
    }

    @Test("Monday 5 to Sunday 11 October 2026, every day drawn, Friday still")
    func days() {
        #expect(F.calendar.component(.day, from: F.weekStart) == 5)
        #expect(week.days.map(\.index) == [0, 1, 2, 3, 4, 5, 6])
        #expect(week.days.filter(\.isStill).map(\.index) == [4])
        #expect(week.days[6].isToday)
    }

    @Test("fifteen moments, thirteen pebbles, two not pebbles yet")
    func counts() {
        #expect(F.moments.count == 15)
        #expect(week.pebbleCount == 13)
        #expect(F.moments.filter { $0.pebble == nil }.map(\.name) == ["Bread from the corner bakery", nil])
    }

    @Test("the picnic names fourteen souls, Bruno's stone is uncarved, one photo is untitled")
    func branches() throws {
        let picnic = try #require(F.moments.first { $0.name == "Picnic on the square, the whole street" })
        #expect(picnic.soulIds.count == 14)
        let bruno = try #require(F.moments.first { $0.name == "Bruno asleep on my feet" })
        #expect(bruno.pebble?.glyph == nil)
        let untitled = try #require(F.moments.first { $0.name == nil && $0.pebble != nil })
        #expect(untitled.firstSnapPath == "fixture/snap-fountain.jpg")
    }

    @Test("the persons of the week read as on the web")
    func persons() {
        let rows = PersonsOfWeek.personsOfWeek(F.moments, names: F.soulNames, weekStart: F.weekStart,
                                               calendar: F.calendar, locale: Vectors.en)
        #expect(rows.map(\.name) == [
            "Mara", "Bruno", "Elio", "Jules", "Nadia",
            "Ana", "Béa", "Cyril", "Dora", "Ezra", "Félix", "Gaby", "Hugo", "Inès"
        ])
        #expect(rows[0].dayCount == 4)
    }

    @Test("every photo has a primed ratio and every carved stone a palette and strokes")
    func data() {
        for path in F.moments.compactMap(\.firstSnapPath) { #expect(F.photos[path] != nil, "\(path)") }
        for moment in F.moments where moment.pebble != nil { #expect(F.palette(for: moment) != nil) }
        #expect(F.moments.compactMap { $0.pebble?.glyph?.strokes.isEmpty }.allSatisfy { !$0 })
        #expect(F.soulGlyphs.count == 14)
    }
}
