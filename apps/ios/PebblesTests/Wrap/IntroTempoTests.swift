import Foundation
import Testing
@testable import Pebbles

/// #1241 D9: the clock plays each sequence slower by its tempo.
@Suite("Intro tempo")
@MainActor
struct IntroTempoTests {
    private let october = CalendarMonth(year: 2026, month: 10)
    private let base = Date(timeIntervalSinceReferenceDate: 1_000)
    private let beats = [IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
                         IntroBeat(key: .row(0), at: 0.32, duration: 0.28, tick: .light)]

    @Test("the cover and the sections each have their factor")
    func factors() {
        #expect(IntroTempo.standard.factor(.cover) == 1.45)
        #expect(IntroTempo.standard.factor(.section(.gem)) == 2.5)
    }

    @Test("a scaled beat starts and lands that many times later, with its key and its tick")
    func scaled() {
        let beat = IntroBeat(key: .row(0), at: 0.32, duration: 0.28, tick: .light).scaled(by: 2.5)
        #expect(abs(beat.at - 0.8) < 1e-12)
        #expect(abs(beat.duration - 0.7) < 1e-12)
        #expect(beat.key == .row(0))
        #expect(beat.tick == .light)
    }

    @Test("the clock plays the cover and a section at their tempo, frames and haptics alike")
    func clockScales() {
        let spy = HapticsSpy()
        let now = base
        let clock = IntroClock(month: october, order: [.cover, .section(.weeks)], ledger: IntroLedger(),
                               haptics: spy, tempo: IntroTempo(cover: 2, sections: 3), now: { now },
                               schedules: false)
        clock.enter(.cover, beats: { beats })
        clock.enter(.section(.weeks), beats: { beats })
        clock.isActive = true
        let start = base.addingTimeInterval(IntroClock.lead)
        // 0.14 s into row 0's beat, which now starts at 0.64 s and takes 0.56 s.
        #expect(abs(clock.reveal(.cover, at: start.addingTimeInterval(0.32 * 2 + 0.14))(.row(0)) - 0.25) < 1e-9)
        #expect(spy.beats == [beats.map { $0.scaled(by: 2) }])
        // The cover lands at its scaled end, 1.2 s, and the weeks play three times slower.
        clock.advance(to: start.addingTimeInterval(1.1))
        #expect(clock.isPlaying(.cover))
        clock.advance(to: start.addingTimeInterval(1.2))
        #expect(clock.isPlaying(.section(.weeks)))
        #expect(spy.beats.last == beats.map { $0.scaled(by: 3) })
    }
}
