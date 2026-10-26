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

    @Test("a week's column places each day by its date: outside the month blank, then empty or its pebbles")
    func weekColumns() throws {
        let wrap = try MonthPayloadFixture.october()
        let columns = WeekColumn.columns(wrap)
        #expect(columns.count == 5)
        // The week of 28 September: Monday to Wednesday are September's.
        let first = try #require(columns.first)
        #expect(first.days.prefix(3).allSatisfy { $0 == .outside })
        #expect(first.days[3] != .outside)
        // The week of 26 October: its Sunday is November's.
        let last = try #require(columns.last)
        #expect(last.days[6] == .outside)
        #expect(last.days[5] != .outside)
        // The 1st carries the cover's pebbles for day 0.
        let october1 = wrap.cover?.days.first { $0.day == 0 }?.pebbles ?? []
        #expect(first.days[3] == (october1.isEmpty ? .empty : .pebbles(october1)))
    }

    @Test("a column's intro shows three pebbles at most a day, and its cairn's stones")
    func weekColumnShape() {
        let pebble = CoverPebble(momentId: "m", emotionId: nil, intensity: 2, polarity: .neutral)
        let quiet = DayWeight(moments: 0, topIntensity: 0)
        let days = [DayWeight(moments: 5, topIntensity: 2)] + Array(repeating: quiet, count: 6)
        let week = MonthWeek(weekStart: CalendarDay(year: 2026, month: 10, day: 5), from: 4, to: 10, pebbles: 5,
                             soulIds: [], days: days)
        let column = WeekColumn(week: week, days: [.pebbles(Array(repeating: pebble, count: 5))]
                                    + Array(repeating: WeekColumnDay.empty, count: 6))
        #expect(column.shape.days.first == 3)
        #expect(column.shape.days.dropFirst().allSatisfy { $0 == 0 })
        #expect(column.shape.stones == 1)
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

    @Test("a milestone's polaroid is square, 3:4 or 4:3, the nearest to its photo, a long photo in a 4:3 or 3:4")
    func polaroidShapes() {
        let wide: CGFloat = 4.0 / 3, tall: CGFloat = 3.0 / 4
        #expect(MilestoneSnap.shape(16.0 / 9) == wide)
        #expect(MilestoneSnap.shape(wide) == wide)
        #expect(MilestoneSnap.shape(1.1) == 1)
        #expect(MilestoneSnap.shape(1) == 1)
        #expect(MilestoneSnap.shape(0.9) == 1)
        #expect(MilestoneSnap.shape(tall) == tall)
        #expect(MilestoneSnap.shape(9.0 / 16) == tall)
        #expect(MilestoneSnap.shape(.nan) == 1)
    }

    @Test("a milestone's polaroid keeps within its box: narrow for a portrait, wide for a landscape")
    func polaroid() {
        let portrait = MilestoneSnap.layout(height: 60, ratio: 9.0 / 16)
        let landscape = MilestoneSnap.layout(height: 60, ratio: 16.0 / 9)
        #expect(abs(portrait.window.width / portrait.window.height - 3.0 / 4) < 1e-9)
        #expect(abs(landscape.window.width / landscape.window.height - 4.0 / 3) < 1e-9)
        #expect(portrait.card.width < landscape.card.width)
        for card in [portrait.card, landscape.card] {
            #expect(card.height <= 60 + 1e-9)
            #expect(card.width <= 60 * 1.15 + 1e-9)
        }
        // Not known yet: square.
        let pending = MilestoneSnap.layout(height: 60, ratio: .nan)
        #expect(abs(pending.window.width - pending.window.height) < 1e-9)
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
