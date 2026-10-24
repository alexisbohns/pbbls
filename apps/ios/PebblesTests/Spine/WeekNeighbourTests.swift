import Foundation
import Testing
@testable import Pebbles

/// #1130 D9: the mouth's link names the pager's neighbour, which skips the empty
/// weeks between (Android lesson, Part 6): week 35 → week 41.
@Suite("WeekNeighbour")
@MainActor
struct WeekNeighbourTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    @Test("week 35's way on is week 41, past the empty weeks")
    func skipsEmptyWeeks() async throws {
        let august = MomentSummary(id: UUID(), name: "Late August", happenedAt: Vectors.at("2026-08-26T10:00:00Z"),
                                   createdAt: Vectors.at("2026-08-26T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
        await model.load { F.moments + [august] }
        let w35 = WeekRollBuilder.weekStart(for: august.happenedAt, calendar: F.calendar)
        let current = try #require(model.page(for: F.weekStart))
        #expect(current.words.previousNumber == 40) // the calendar's, read only by nothing that links
        let older = try #require(model.page(for: w35))
        #expect(older.next == WeekNeighbour(number: 41, weekStart: F.weekStart))
        #expect(current.next == nil)
    }
}
