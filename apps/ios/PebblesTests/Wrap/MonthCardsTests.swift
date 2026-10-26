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

    @Test("the milestones' intro stamps each stone at its pebble's intensity, then its date, name and souls")
    func milestonesIntro() throws {
        let wrap = try MonthPayloadFixture.october()
        let lookups = MonthPayloadFixture.lookups(wrap)
        let beats = MilestonesSection.introBeats(wrap: wrap, lookups: lookups)
        let moments = (wrap.milestones ?? []).compactMap(lookups.moment)
        #expect(!moments.isEmpty)
        for (row, moment) in moments.enumerated() {
            let impact = try #require(beats.first { $0.key == .impact(row) })
            #expect(impact.tick == .seal(moment.pebble?.intensity ?? 1))
            let date = try #require(beats.first { $0.key == .date(row) })
            let name = try #require(beats.first { $0.key == .name(row) })
            #expect(date.at < name.at)
        }
    }

    @Test("on a card, the persons share the room left by more, their glyphs and rings growing, within bounds")
    func personsFill() {
        let roomy = PersonsSection.rowSize(room: 500, rows: 5)
        #expect(roomy.height == (500 - MonthLayout.moreReserve) / 5)
        #expect(roomy.glyph > SpineLayout.mark)
        #expect(roomy.glyph <= PersonsSection.glyphCap)
        #expect(roomy.ring > MonthLayout.rowRing)
        #expect(roomy.ring <= PersonsSection.ringCap)
        let tight = PersonsSection.rowSize(room: 200, rows: 5)
        #expect(tight.height == SpineLayout.rowMin)
        #expect(tight.glyph == SpineLayout.mark)
        #expect(tight.ring == MonthLayout.rowRing)
        #expect(PersonsSection.rowSize(room: 2000, rows: 2).height == PersonsSection.rowCap)
    }

    @Test("the domains' intro fills each gauge to its share of the first, the fullest swelling most")
    func domainsIntro() throws {
        let wrap = try MonthPayloadFixture.october()
        let beats = DomainsSection.introBeats(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        let first = try #require(beats.first { $0.key == .gauge(0) })
        let second = try #require(beats.first { $0.key == .gauge(1) })
        #expect(first.duration == IntroScore.fillSpan(1))
        #expect(first.duration > second.duration)
        #expect((first.swell?.to ?? 0) > (second.swell?.to ?? 1))
        #expect(beats.contains { $0.key == .more })
    }

    @Test("on a card, the milestones share its room and their stones grow to the Spine's own; the list keeps its size")
    func milestonesFill() {
        let list = MilestonesSection.rowSize(room: nil, rows: 5)
        #expect(list.height == MilestonesSection.listRow)
        #expect(list.stone == MilestonesSection.listStone)
        #expect(list.snap == MilestonesSection.listSnap)
        #expect(list.soul == SoulMarks.side)
        let card = MilestonesSection.rowSize(room: 400, rows: 5)
        #expect(card.height == 80)
        #expect(card.stone > list.stone)
        #expect(card.snap > list.snap)
        #expect(card.soul > list.soul)
        let roomy = MilestonesSection.rowSize(room: 900, rows: 2)
        #expect(roomy.height == MilestonesSection.rowCap)
        #expect(roomy.stone == 1)
        #expect(roomy.soul <= WeeksSummary.soulCap)
        #expect(MilestonesSection.rowSize(room: 100, rows: 5).height == MilestonesSection.listRow)
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
