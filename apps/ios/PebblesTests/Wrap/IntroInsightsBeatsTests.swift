import Foundation
import Testing
@testable import Pebbles

/// #1241: the insights head over a figure that draws nothing plays alone.
@Suite("Intro: tone and hours beats on a wrap")
@MainActor
struct IntroInsightsBeatsTests {
    private func wrap(tone: Tone?? = nil, hours: MonthHours?? = nil) throws -> MonthWrap {
        let base = try MonthPayloadFixture.october()
        return MonthWrap(month: base.month, daysInMonth: base.daysInMonth, firstWeekday: base.firstWeekday,
                         pebbles: base.pebbles, people: base.people, sections: base.sections, cover: base.cover,
                         weeks: base.weeks, milestones: base.milestones, persons: base.persons,
                         previousMonth: base.previousMonth, gem: base.gem, fineEmotions: base.fineEmotions,
                         domains: base.domains, tone: tone ?? base.tone, hours: hours ?? base.hours)
    }

    @Test("hours with no known moments: nothing, or the insights head alone")
    func emptyHours() throws {
        let empty = try wrap(hours: .some(MonthHours(items: [], busiest: nil)))
        let lookups = MonthPayloadFixture.lookups(empty)
        #expect(HoursFigure.introBeats(wrap: empty, lookups: lookups, insights: false).isEmpty)
        #expect(HoursFigure.introBeats(wrap: empty, lookups: lookups, insights: true).map(\.key) == [.insights])
        let none = try wrap(hours: .some(nil))
        #expect(HoursFigure.introBeats(wrap: none, lookups: lookups, insights: true).map(\.key) == [.insights])
    }

    @Test("tone nil or without days: nothing, or the insights head alone")
    func emptyTone() throws {
        let base = try MonthPayloadFixture.october()
        let tone = try #require(base.tone)
        let noDays = try wrap(tone: .some(Tone(days: [], verdict: tone.verdict, first: tone.first, last: tone.last)))
        let noTone = try wrap(tone: .some(nil))
        for empty in [noDays, noTone] {
            #expect(ToneFigure.introBeats(wrap: empty, insights: false).isEmpty)
            #expect(ToneFigure.introBeats(wrap: empty, insights: true).map(\.key) == [.insights])
        }
    }

    @Test("the fixture's own tone and hours still play their sweep and stones")
    func fixtureUnaffected() throws {
        let base = try MonthPayloadFixture.october()
        let lookups = MonthPayloadFixture.lookups(base)
        for insights in [false, true] {
            #expect(ToneFigure.introBeats(wrap: base, insights: insights).contains { $0.key == .sweep })
            #expect(HoursFigure.introBeats(wrap: base, lookups: lookups, insights: insights)
                .contains { $0.key == .dial })
        }
    }
}
