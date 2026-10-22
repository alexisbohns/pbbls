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

    @Test("at the default size a day with moments keeps the spec's 50 under its seam, a still day none")
    func padAtLarge() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        for day in page.week.days {
            let pad = SpineWeekRows.seamPad(day, dynamicTypeSize: .large, legibilityWeight: .regular)
            #expect(pad == (day.isStill ? 0 : SpineLayout.dayPadTop - SpineLayout.seamHeight), "day \(day.index)")
        }
    }

    @Test("a tall label grows the pad until its text clears the first ring by 4")
    func padGrows() async throws {
        // A 100 pt tab, its text 95; the ring 12 pt above the row: 95 + 4 − 22 + 12.
        #expect(SpineLayout.seamPad(labelHeight: 100, ringTop: -12) == 89)
        #expect(SpineLayout.seamPad(labelHeight: 40, ringTop: -12) == 50)

        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        let label = DaySeam.labelHeight(dynamicTypeSize: .accessibility2, legibilityWeight: .regular)
        for day in page.week.days {
            guard let first = day.moments.first else { continue }
            let pad = SpineWeekRows.seamPad(day, dynamicTypeSize: .accessibility2, legibilityWeight: .regular)
            let ring = SpinePebble.ringTop(for: first, dynamicTypeSize: .accessibility2, legibilityWeight: .regular)
            #expect(pad > SpineLayout.dayPadTop - SpineLayout.seamHeight, "day \(day.index)")
            let seam = SpineLayout.seamOverhang + SpineLayout.seamHeight
            let gap = seam + pad + ring - (label - SpineLayout.labelPadBottom)
            // Exactly 4 where the ring rises above the row; more where it starts below it.
            #expect(gap >= SpineLayout.labelClearance - 0.001, "day \(day.index)")
        }
    }

    @Test("the verso opens with the masthead, the opening and the persons, and the page ends at the mouth")
    func versoAndClosing() async throws {
        let model = await SpineFixtureWeek.model()
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening", "persons"])
        #expect(SpineWeekRows.closing(page).map(\.id) == ["mouth"])
    }

    @Test("an empty current week has no persons, no days and the fresh week instead of the mouth")
    func emptyWeek() async throws {
        let model = PathModel(calendar: SpineFixtureWeek.calendar, now: { SpineFixtureWeek.today })
        await model.load { [] }
        let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
        #expect(SpineWeekRows.verso(page).map(\.id) == ["masthead", "opening"])
        #expect(SpineWeekRows.days(page).isEmpty)
        #expect(SpineWeekRows.closing(page).map(\.id) == ["empty"])
    }
}
