import Foundation
import Testing
@testable import Pebbles

/// The strip's words (#1226), Android's `path_strip_*` strings in both
/// languages; the date is the Spine's localised day and month.
@Suite("Wrap copy · strip")
struct WrapCopyStripTests {
    private let calendar = Vectors.paris
    private let october5 = CalendarDay(year: 2026, month: 10, day: 5)

    private func week(moments: Int, focusable: Bool = true, live: Bool = false) -> StripWeek {
        StripWeek(weekStart: october5, isoWeek: 41, isoYear: 2026, moments: moments, isFocusable: focusable,
                  isLive: live, days: [])
    }

    private func say(_ week: StripWeek, _ locale: Locale) -> String {
        WrapCopy.stripWeek(week, calendar: calendar, locale: locale, dateLocale: locale)
    }

    @Test("English")
    func english() {
        let en = Vectors.en
        #expect(say(week(moments: 4), en) == "Week of October 5, 4 moments")
        #expect(say(week(moments: 1), en) == "Week of October 5, 1 moment")
        #expect(say(week(moments: 3, live: true), en) == "This week, from October 5, 3 moments so far")
        #expect(say(week(moments: 0, focusable: false), en) == "Week of October 5, no moments")
        #expect(WrapCopy.stripMonth(CalendarMonth(year: 2026, month: 9), locale: en) == "September, the month")
        #expect(WrapCopy.stripNow(locale: en) == "now")
        #expect(WrapCopy.monthShort(CalendarMonth(year: 2026, month: 10), locale: en) == "Oct")
    }

    @Test("French")
    func french() {
        let fr = Vectors.fr
        #expect(say(week(moments: 4), fr) == "Semaine du 5 octobre, 4 moments")
        #expect(say(week(moments: 1), fr) == "Semaine du 5 octobre, 1 moment")
        #expect(say(week(moments: 3, live: true), fr) == "Cette semaine, depuis le 5 octobre, 3 moments pour l'instant")
        #expect(say(week(moments: 0, focusable: false), fr) == "Semaine du 5 octobre, aucun moment")
        #expect(WrapCopy.stripMonth(CalendarMonth(year: 2026, month: 9), locale: fr) == "Septembre, le mois")
        #expect(WrapCopy.stripNow(locale: fr) == "maintenant")
        #expect(WrapCopy.monthShort(CalendarMonth(year: 2026, month: 10), locale: fr) == "oct.")
    }
}
