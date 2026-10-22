import Foundation

/// One day of a Spine week (#1130). `date` is midnight local; `index` 0 is Monday.
struct SpineDay<M: SpineMoment> {
    let date: Date
    let index: Int
    let moments: [M]
    /// A past or current day with no moment: a seam with *still* under it.
    let isStill: Bool
    let isToday: Bool
}

struct SpineWeek<M: SpineMoment> {
    let weekStart: Date
    let days: [SpineDay<M>]
    /// Completed moments only; a bare moment takes its place but is not counted.
    let pebbleCount: Int
}

/// The week as the Spine reads it, a port of `apps/web/lib/utils/spine/spine-week.ts`:
/// a book, so days run oldest first in every week (the current one included) and
/// moments run by `(happenedAt, spineId)` within a day. A past empty day is kept
/// and still; a day after today is dropped unless it holds a moment.
enum SpineWeekBuilder {
    static func spineWeek<M: SpineMoment>(
        weekStart: Date, moments: [M], today: Date, calendar: Calendar
    ) -> SpineWeek<M> {
        var byDay = Array(repeating: [M](), count: 7)
        for moment in moments {
            let index = LocalDays.dayIndexInWeek(weekStart: weekStart, moment.happenedAt, calendar: calendar)
            if (0...6).contains(index) { byDay[index].append(moment) }
        }
        let todayStart = calendar.startOfDay(for: today)
        var days: [SpineDay<M>] = []
        for index in 0..<7 {
            let date = LocalDays.adding(index, to: weekStart, calendar: calendar)
            let sorted = byDay[index].sorted { lhs, rhs in
                lhs.happenedAt != rhs.happenedAt ? lhs.happenedAt < rhs.happenedAt : lhs.spineId < rhs.spineId
            }
            if date > todayStart && sorted.isEmpty { continue }
            days.append(SpineDay(date: date, index: index, moments: sorted, isStill: sorted.isEmpty,
                                 isToday: date == todayStart))
        }
        let pebbleCount = byDay.joined().filter(\.isPebble).count
        return SpineWeek(weekStart: weekStart, days: days, pebbleCount: pebbleCount)
    }

    /// The week's moments in reading order: every day, top to bottom.
    static func readingOrder<M: SpineMoment>(_ week: SpineWeek<M>) -> [M] {
        week.days.flatMap(\.moments)
    }
}
