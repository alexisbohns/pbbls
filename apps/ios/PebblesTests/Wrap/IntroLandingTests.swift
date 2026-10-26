import Foundation
import Testing
@testable import Pebbles

/// #1241 D11: every throw of the deck lands on a tick.
@Suite("Intro landing")
@MainActor
struct IntroLandingTests {
    private let base = Date(timeIntervalSinceReferenceDate: 1_000)
    private let weeks = IntroSequence.section(.weeks)
    private let beats = [IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
                         IntroBeat(key: .row(0), at: 0.32, duration: 0.28, tick: .light)]

    @Test("a card playing its intro ticks its own arrival; a settled card gets the same tick; an idle page none")
    func landedTicks() {
        let spy = HapticsSpy()
        let now = base
        let clock = IntroClock(month: CalendarMonth(year: 2026, month: 10), order: [.cover, weeks],
                               ledger: IntroLedger(), haptics: spy, tempo: IntroTempo(cover: 1, sections: 1),
                               now: { now }, schedules: false)
        clock.register(.cover, beats: { beats })
        clock.register(weeks, beats: { beats })
        clock.isActive = true
        clock.show(.cover)
        clock.landed(.cover)
        // Playing: its own arrival ticks, not a second one.
        #expect(spy.ticks.isEmpty)
        clock.advance(to: base.addingTimeInterval(1))
        clock.show(weeks)
        clock.advance(to: base.addingTimeInterval(2))
        // Back on the cover, already played: the landing ticks.
        clock.show(.cover)
        clock.landed(.cover)
        #expect(spy.ticks == [.arrive])
        // A page that does not play stays silent.
        clock.isActive = false
        clock.landed(weeks)
        #expect(spy.ticks == [.arrive])
    }
}
