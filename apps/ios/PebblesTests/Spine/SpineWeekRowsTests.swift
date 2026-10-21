import Foundation
import Testing
@testable import Pebbles

/// #1130 §Geometry: per day a seam then its pebbles; 58 above a day's first
/// pebble (carried by the seam row), 38 between pebbles, 34 below the last,
/// less the next seam's 14 pt overhang, except under the week's last day.
@Suite("Spine week rows")
@MainActor
struct SpineWeekRowsTests {
    @Test("the fixture week: seven seams, fifteen pebbles, in reading order")
    func order() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let rows = SpineWeekRows.days(page)
        #expect(rows.filter { if case .seam = $0 { return true } else { return false } }.count == 7)
        let pebbles = rows.compactMap { row -> MomentSummary? in
            if case let .pebble(moment, _, _) = row { return moment } else { return nil }
        }
        #expect(pebbles.map(\.id) == page.contents.map(\.id))
    }

    @Test("bottom pads: 38 inside a day, 20 before the next seam, 34 under the last day")
    func pads() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let pads = SpineWeekRows.days(page).compactMap { row -> CGFloat? in
            if case let .pebble(_, _, bottom) = row { return bottom } else { return nil }
        }
        #expect(pads.first == SpineLayout.pebbleGap)
        #expect(pads.contains(SpineLayout.dayPadBottom - SpineLayout.seamOverhang))
        #expect(pads.last == SpineLayout.dayPadBottom)
    }

    @Test("a slice keeps only the days asked for")
    func slice() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let rows = SpineWeekRows.days(page, only: 4...4)
        #expect(rows.count == 1)
        guard case let .seam(day, _) = rows[0] else { Issue.record("Friday is a seam"); return }
        #expect(day.isStill)
    }
}
