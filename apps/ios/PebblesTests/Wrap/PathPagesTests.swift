import Foundation
import Testing
@testable import Pebbles

/// The strip as drawn and its pages (#1226). Android: `PathPagesTest`, case for case.
@Suite("Path pages")
struct PathPagesTests {
    private let calendar = Vectors.paris
    private let june1 = CalendarDay(year: 2026, month: 6, day: 1)
    private let june22 = CalendarDay(year: 2026, month: 6, day: 22)
    private let june29 = CalendarDay(year: 2026, month: 6, day: 29)
    private let july6 = CalendarDay(year: 2026, month: 7, day: 6)
    private let june = CalendarMonth(year: 2026, month: 6)
    private var today: Date { Vectors.local(2026, 6, 11, 12) } // Saturday 11 July

    private func date(_ day: CalendarDay) -> Date {
        day.date(in: calendar.timeZone) ?? .distantPast
    }

    private func entry(_ day: CalendarDay, moments: Int = 0) -> WeekRollEntry {
        let start = date(day)
        return WeekRollEntry(weekStart: start, moments: (0..<moments).map { index in
            MomentSummary(id: UUID(), name: "\(day.iso)-\(index)", happenedAt: start.addingTimeInterval(3600),
                          createdAt: start, firstSnapPath: nil, pebble: nil)
        })
    }

    private func week(_ day: CalendarDay, focusable: Bool = true, live: Bool = false) -> StripItem {
        .week(StripWeek(weekStart: day, isoWeek: 0, isoYear: 2026, moments: focusable ? 1 : 0, isFocusable: focusable,
                        isLive: live, days: (0..<7).map { DayWeight(moments: $0 == 0 && focusable ? 1 : 0,
                                                                    topIntensity: 0) }))
    }

    // MARK: - Items

    @Test("the server's strip is kept as sent, months and spacers included")
    func keptAsSent() {
        let strip = Strip(items: [week(june1), week(june22, focusable: false), week(june29),
                                  .month(StripMonth(month: june, pebbles: 4)), week(july6, live: true)])
        let items = PathPages.items(strip: strip, entries: [entry(june1, moments: 1), entry(june29, moments: 1),
                                                            entry(july6)], today: today, calendar: calendar)
        #expect(items == strip.items)
        #expect(PathPages.pages(items, calendar: calendar)
                == [.week(date(june1)), .week(date(june29)), .month(june), .week(date(july6))])
    }

    @Test("without a strip the feed's weeks stand in, with blank cairns")
    func feedStandsIn() throws {
        let items = PathPages.items(strip: nil, entries: [entry(june1, moments: 2), entry(july6)], today: today,
                                    calendar: calendar)
        #expect(PathPages.pages(items, calendar: calendar) == [.week(date(june1)), .week(date(july6))])
        guard case let .week(first) = try #require(items.first) else { Issue.record("a week first"); return }
        #expect(first.moments == 2)
        #expect(first.days.isEmpty)
        #expect(first.isoWeek == 23)
        guard case let .week(last) = try #require(items.last) else { Issue.record("a week last"); return }
        #expect(last.isLive)
    }

    @Test("a week the strip has not seen yet joins in date order, before the month it precedes")
    func lateWeekJoins() {
        // The strip lags a write: June 29 holds a moment now, and June closes in that week.
        let strip = Strip(items: [week(june1), week(june22, focusable: false),
                                  .month(StripMonth(month: june, pebbles: 4)), week(july6, live: true)])
        let items = PathPages.items(strip: strip, entries: [entry(june1, moments: 1), entry(june22, moments: 1),
                                                            entry(june29, moments: 1), entry(july6)],
                                    today: today, calendar: calendar)
        #expect(PathPages.pages(items, calendar: calendar)
                == [.week(date(june1)), .week(date(june22)), .week(date(june29)), .month(june), .week(date(july6))])
        // The spacer is replaced in place.
        #expect(items.count == 5)
    }

    @Test("the server's verbatim strip is drawn exactly as sent, months after the week of their last day")
    func verbatimStrip() throws {
        // The harness's reader: August 2026 ends on Monday the 31st, the edge of "after the week of its last day".
        let strip = try WrapPayloads.strip()
        let liveDay = Vectors.local(2026, 9, 21, 12)
        #expect(PathPages.items(strip: strip, entries: [], today: liveDay, calendar: calendar) == strip.items)
    }

    @Test("a feed-only week at the ISO year's end keeps its week and year")
    func isoYearBoundary() throws {
        let december28 = CalendarDay(year: 2026, month: 12, day: 28)
        let january4 = CalendarDay(year: 2027, month: 1, day: 4)
        let items = PathPages.items(strip: nil, entries: [entry(december28, moments: 1), entry(january4, moments: 1)],
                                    today: Vectors.local(2027, 0, 6, 12), calendar: calendar)
        let weeks = items.compactMap { item -> StripWeek? in
            if case let .week(week) = item { return week }
            return nil
        }
        #expect(weeks.map(\.isoWeek) == [53, 1])
        #expect(weeks.map(\.isoYear) == [2026, 2027])
    }

    @Test("a strip kept across a Monday leaves one live week, the current one")
    func oneLiveWeek() {
        let strip = Strip(items: [week(june29, live: true)])
        let items = PathPages.items(strip: strip, entries: [entry(june29, moments: 1), entry(july6)], today: today,
                                    calendar: calendar)
        let live = items.compactMap { item -> CalendarDay? in
            if case let .week(week) = item, week.isLive { return week.weekStart }
            return nil
        }
        #expect(live == [july6])
    }

    // MARK: - Refocus

    private var pages: [PathPage] { [.week(date(june1)), .month(june), .week(date(july6))] }

    @Test("keeps an existing focus, a month included")
    func keepsFocus() {
        #expect(PathPages.refocused(pages, focused: .week(date(june1)), today: today, calendar: calendar)
                == .week(date(june1)))
        #expect(PathPages.refocused(pages, focused: .month(june), today: today, calendar: calendar) == .month(june))
    }

    @Test("prefers the current week when focus is unset or vanished")
    func prefersCurrent() {
        #expect(PathPages.refocused(pages, focused: nil, today: today, calendar: calendar) == .week(date(july6)))
        #expect(PathPages.refocused(pages, focused: .week(date(june22)), today: today, calendar: calendar)
                == .week(date(july6)))
    }

    @Test("falls back to the closest page when the current week is absent, a month by its last day")
    func closest() {
        let past: [PathPage] = [.week(date(june1)), .month(june)]
        #expect(PathPages.refocused(past, focused: .week(date(june29)), today: today, calendar: calendar)
                == .month(june))
        #expect(PathPages.refocused(past, focused: .week(date(CalendarDay(year: 2026, month: 6, day: 8))),
                                    today: today, calendar: calendar) == .week(date(june1)))
    }

    @Test("nil only without pages")
    func nilWithoutPages() {
        #expect(PathPages.refocused([], focused: nil, today: today, calendar: calendar) == nil)
    }

    @Test("a month's last day knows its length")
    func lastDay() {
        #expect(CalendarMonth(year: 2026, month: 2).lastDay == CalendarDay(year: 2026, month: 2, day: 28))
        #expect(CalendarMonth(year: 2028, month: 2).lastDay == CalendarDay(year: 2028, month: 2, day: 29))
        #expect(CalendarMonth(year: 2026, month: 10).lastDay == CalendarDay(year: 2026, month: 10, day: 31))
    }
}
