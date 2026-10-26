import Foundation
import Testing
@testable import Pebbles

/// #1241 D10, D12, D13: the month's cards, their content and their signatures.
@Suite("Month cards")
@MainActor
struct MonthCardsTests {
    @Test("every week of the month is listed, an empty one too, and the head counts them all")
    func everyWeek() throws {
        let wrap = try Self.quietSecondWeek()
        let weeks = WeeksSummary.weeks(wrap)
        #expect(weeks.count == 5)
        #expect(weeks[1].pebbles == 0)
        #expect(WeeksSummary.isShown(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap)))
        #expect(WeeksSummary.head(wrap: wrap).label == WrapCopy.weeksHeader(5, locale: SpineStrings.appLocale))
    }

    @Test("a month whose weeks are all empty shows no weeks")
    func allEmpty() throws {
        let october = try MonthPayloadFixture.october()
        let wrap = Self.with(october, weeks: (october.weeks ?? []).map(Self.emptied))
        #expect(!WeeksSummary.isShown(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap)))
    }

    @Test("the weeks' intro: each week its cairn's stones and its named souls; an empty week whole")
    func weeksIntro() throws {
        let wrap = try Self.quietSecondWeek()
        let lookups = MonthPayloadFixture.lookups(wrap)
        let beats = WeeksSummary.introBeats(wrap: wrap, lookups: lookups)
        let first = try #require(WeeksSummary.weeks(wrap).first)
        let stones = CairnGeometry.widths(first.days).count
        #expect(beats.contains { $0.key == .cairn(0, stones - 1) })
        #expect(!beats.contains { $0.key == .cairn(0, stones) })
        #expect(try #require(beats.first { $0.key == .row(1) }).tick == .faint)
        #expect(!beats.contains { $0.key == .cairn(1, 0) })
        let second = WeeksSummary.weeks(wrap)[2]
        let souls = SoulMarks.shown(second.soulIds, lookups: lookups, limit: WeeksSummary.soulLimit).count
        #expect(beats.contains { $0.key == .mark(2, souls - 1) } == (souls > 0))
        #expect(!beats.contains { $0.key == .mark(2, souls) })
    }

    @Test("on a card, the weeks share its room, within the row minimum and the cap, their cairns growing")
    func weeksFill() {
        #expect(WeeksSummary.rowHeight(room: 400, weeks: 5) == 80)
        #expect(WeeksSummary.rowHeight(room: 100, weeks: 6) == SpineLayout.rowMin)
        #expect(WeeksSummary.rowHeight(room: 900, weeks: 4) == WeeksSummary.rowCap)
        #expect(WeeksSummary.cairnScale(rowHeight: SpineLayout.rowMin) == 1)
        #expect(WeeksSummary.cairnScale(rowHeight: 80) > 1)
        #expect(WeeksSummary.cairnScale(rowHeight: WeeksSummary.rowCap) <= WeeksSummary.cairnCap)
    }

    /// October with its second week emptied, as the server sends a week without a pebble.
    static func quietSecondWeek() throws -> MonthWrap {
        let october = try MonthPayloadFixture.october()
        var weeks = october.weeks ?? []
        weeks[1] = emptied(weeks[1])
        return with(october, weeks: weeks)
    }

    static func emptied(_ week: MonthWeek) -> MonthWeek {
        MonthWeek(weekStart: week.weekStart, from: week.from, to: week.to, pebbles: 0, soulIds: [],
                  days: week.days.map { _ in DayWeight(moments: 0, topIntensity: 0) })
    }

    static func with(_ wrap: MonthWrap, weeks: [MonthWeek]) -> MonthWrap {
        MonthWrap(month: wrap.month, daysInMonth: wrap.daysInMonth, firstWeekday: wrap.firstWeekday,
                  pebbles: wrap.pebbles, people: wrap.people, sections: wrap.sections, cover: wrap.cover,
                  weeks: weeks, milestones: wrap.milestones, persons: wrap.persons,
                  previousMonth: wrap.previousMonth, gem: wrap.gem, fineEmotions: wrap.fineEmotions,
                  domains: wrap.domains, tone: wrap.tone, hours: wrap.hours)
    }
}
