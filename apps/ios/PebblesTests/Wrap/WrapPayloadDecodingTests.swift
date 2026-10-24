import Foundation
import Testing
@testable import Pebbles

/// The wraps decoded from the server's own payloads (#1226, the foreign-payload
/// rule): the strip of a reader seeded from July to September, the fixture's
/// weeks 40–42 and a live week, its October, a thin May and a quiet June. The
/// expected values are the ones the harness asserts on the server and Android's
/// `WrapPayloadDecodingTest` asserts on its side.
@Suite("Path wrap payload decoding")
struct WrapPayloadDecodingTests {
    @Test("the strip runs from the first moment's week to the live week, months after their last week")
    func strip() throws {
        let items = try WrapPayloads.strip().items
        guard case let .week(first) = items.first else { Issue.record("the strip opens on a week"); return }
        #expect(first.weekStart == CalendarDay(year: 2026, month: 7, day: 6))
        guard case let .week(last) = items.last else { Issue.record("the strip ends on a week"); return }
        #expect(last.isLive && last.isFocusable)

        let months = items.indices.compactMap { index -> (index: Int, month: StripMonth)? in
            guard case let .month(month) = items[index] else { return nil }
            return (index, month)
        }
        #expect(months.map(\.month) == [
            StripMonth(month: CalendarMonth(year: 2026, month: 7), pebbles: 1),
            StripMonth(month: CalendarMonth(year: 2026, month: 8), pebbles: 3),
            StripMonth(month: CalendarMonth(year: 2026, month: 9), pebbles: 1)
        ])
        let weeksBefore = months.map { pair -> CalendarDay? in
            guard case let .week(week) = items[pair.index - 1] else { return nil }
            return week.weekStart
        }
        #expect(weeksBefore == [
            CalendarDay(year: 2026, month: 7, day: 27),
            CalendarDay(year: 2026, month: 8, day: 31),
            CalendarDay(year: 2026, month: 9, day: 28)
        ])

        let aug10 = try #require(items.lazy.compactMap { item -> StripWeek? in
            guard case let .week(week) = item, week.weekStart == CalendarDay(year: 2026, month: 8, day: 10) else {
                return nil
            }
            return week
        }.first)
        #expect(aug10.days[2] == DayWeight(moments: 2, topIntensity: 3))
        #expect(aug10.days[4] == DayWeight(moments: 1, topIntensity: 0))
    }

    @Test("a full week carries every tile and its data")
    func fullWeek() throws {
        let week = try WrapPayloads.week("week-full")
        #expect(week.tiles == WeekTile.allCases)
        #expect(week.pebbles == 11)
        #expect(week.people == 5)
        let persons = try #require(week.persons)
        #expect(persons.first?.count == 4)
        #expect(persons.first?.days.days == [0, 1, 3, 5])
        let families = try #require(week.families)
        #expect(families.map(\.slug) == ["joy", "peace", "pride", "anger", "sadness", "fear"])
        #expect(families.first?.worth == 7)
        #expect(week.lead?.zero == ["shame"])
        #expect(week.weight == WeightGrid(bright: [0, 2, 3], even: [2, 1, 0], heavy: [1, 2, 0]))
        #expect(week.domains?.count == 9)
        #expect(week.hours?.count == 11)
        #expect(week.hours?.first?.happened == 10.25)
        #expect(week.kept?.photoMomentIds.count == 5)
        #expect(week.kept?.cards.map(\.count) == [2, 1, 1, 3, 1, 2, 1])
    }

    @Test("a week with nobody named carries the invitation's moments, and no stats")
    func nobodyNamed() throws {
        let week = try WrapPayloads.week("week-nobody-named")
        #expect(week.tiles == [.contents, .persons])
        #expect(week.persons == [])
        #expect(week.inviteMomentIds?.count == 3)
        #expect(week.families == nil)
        #expect(week.weight == nil)
    }

    @Test("one pebble gets no tile, and the live week no stats")
    func onePebbleAndLive() throws {
        let one = try WrapPayloads.week("week-one-pebble")
        #expect(one.tiles == [])
        #expect(one.persons == nil)
        let live = try WrapPayloads.week("week-live")
        #expect(live.isLive)
        #expect(live.tiles == [.contents, .persons])
        #expect(live.families == nil)
    }

    @Test("October carries every section")
    func october() throws {
        let month = try WrapPayloads.month("month-full")
        #expect(month.month == CalendarMonth(year: 2026, month: 10))
        #expect(month.daysInMonth == 31)
        #expect(month.firstWeekday == 3) // Thursday
        #expect(month.pebbles == 24)
        #expect(month.sections == MonthSection.allCases)
        let cover = try #require(month.cover)
        #expect(cover.days.count == 19)
        #expect(cover.days.first?.pebbles.first?.polarity == .lowlight)
        #expect(month.weeks?.map(\.pebbles) == [1, 11, 3, 4, 5])
        #expect(month.milestones?.count == 5)
        #expect(month.persons?.map(\.isNewSincePrevious) == [false, false, true, false, true, true])
        #expect(month.previousMonth?.pebbles == 3)
        let gem = try #require(month.gem)
        #expect(gem.facets.count == 28)
        #expect(gem.main == "joy")
        #expect(gem.worths["joy"] == 14)
        let tone = try #require(month.tone)
        #expect(tone.verdict == .brightTwoToOne)
        #expect(tone.first == .lowlight)
        #expect(tone.last == .highlight)
        #expect(tone.days.count == 31)
        #expect(tone.days[0] == ToneDay(up: 0, even: 0, down: 3))
        #expect(tone.days[4] == ToneDay(up: 5, even: 0, down: 0))
        #expect(month.hours?.items.count == 24)
        #expect(month.hours?.busiest?.count == 24)
    }

    @Test("a thin month keeps every section, a quiet month only its cover and weeks")
    func thinAndQuiet() throws {
        let thin = try WrapPayloads.month("month-thin")
        #expect(thin.sections == MonthSection.allCases)
        #expect(thin.milestones?.count == 3)
        let quiet = try WrapPayloads.month("month-quiet")
        #expect(quiet.sections == [.weeks])
        #expect(quiet.cover?.days.count == 2)
        #expect(quiet.persons == nil)
        #expect(quiet.gem == nil)
        #expect(quiet.tone == nil)
    }
}
