import SwiftUI
import Testing
@testable import Pebbles

/// A month's tone on the disc (#1306): muted on its served state alone; a kept
/// month wears its family's hue, or ink when the family is absent or unresolved,
/// so a rim month with a family this build does not know still shows its success.
@Suite("Year disc tone")
@MainActor
struct YearDiscToneTests {
    typealias Month = AchievementsPage.Month

    static func month(state: AchievementsPage.MonthState, family: String?, level: Int) -> Month {
        Month(month: 8, state: state, family: family, momentDays: 20, pebbleDays: 18, fullWeeks: 4, weeksFilled: 4,
              level: level, progress: 0, done: AchievementsPage.MonthDone(moments: true, pebbles: true, weeks: true))
    }

    @Test("a kept month in a known family wears its hue")
    func known() throws {
        let joy = try #require(SpineFixtureWeek.families.first { $0.slug == "joy" })
        let lookups = WrapLookups(family: { $0 == "joy" ? joy : nil })
        let inks = AchievementInks(scheme: .light)
        #expect(YearDisc.tone(Self.month(state: .kept, family: "joy", level: 2), lookups: lookups, inks: inks)
            == inks.hue(joy.palette))
    }

    @Test("a kept rim month in an unknown or absent family draws in ink, never muted")
    func unknown() {
        let inks = AchievementInks(scheme: .dark)
        let lookups = WrapLookups()
        #expect(YearDisc.tone(Self.month(state: .kept, family: "awe", level: 2), lookups: lookups, inks: inks)
            == inks.ink)
        #expect(YearDisc.tone(Self.month(state: .kept, family: nil, level: 1), lookups: lookups, inks: inks)
            == inks.ink)
    }

    @Test("an upcoming or quiet month is muted, whatever its family")
    func muted() {
        let inks = AchievementInks(scheme: .light)
        for state in [AchievementsPage.MonthState.upcoming, .quiet] {
            #expect(YearDisc.tone(Self.month(state: state, family: "joy", level: 0), lookups: WrapLookups(), inks: inks)
                == nil)
        }
        #expect(YearDisc.tone(nil, lookups: WrapLookups(), inks: inks) == nil)
    }
}
