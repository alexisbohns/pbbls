import Foundation
import Testing
@testable import Pebbles

/// A stand-in that records what the clock asked of the haptics (also `IntroTempoTests`').
@MainActor
final class HapticsSpy: IntroHapticPlaying {
    var prepared = 0
    var played: [IntroSequence] = []
    var beats: [[IntroBeat]] = []
    var starts: [Date] = []
    var stopped: [IntroSequence] = []
    var stoppedMonths: [CalendarMonth] = []

    func prepare() { prepared += 1 }

    func play(_ sequence: IntroSequence, of month: CalendarMonth, beats: [IntroBeat], startingAt begin: Date) {
        played.append(sequence)
        self.beats.append(beats)
        starts.append(begin)
    }

    func stop(_ sequence: IntroSequence, of month: CalendarMonth) {
        stopped.append(sequence)
        stoppedMonths.append(month)
    }

    var ticks: [IntroTick] = []

    func tick(_ tick: IntroTick) { ticks.append(tick) }
}

/// #1241 D3, D4, §Lifecycle.
@Suite("Intro clock")
@MainActor
struct IntroClockTests {
    private let october = CalendarMonth(year: 2026, month: 10)
    private let base = Date(timeIntervalSinceReferenceDate: 1_000)
    private let weeks = IntroSequence.section(.weeks)
    /// Lands at 0.6 s (0.32 + 0.28).
    private let beats = [IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
                         IntroBeat(key: .row(0), at: 0.32, duration: 0.28, tick: .light)]

    /// At tempo 1, so the beats play at the times they name (`IntroTempoTests` pins the scaling).
    private func clock(_ ledger: IntroLedger? = nil, haptics: HapticsSpy? = nil) -> IntroClock {
        let now = base
        return IntroClock(month: october, order: [.cover, weeks, .section(.persons)], ledger: ledger ?? IntroLedger(),
                          haptics: haptics, tempo: IntroTempo(cover: 1, sections: 1), now: { now }, schedules: false)
    }

    @Test("a sequence waits hidden, plays once the page is active, and lands at its end")
    func plays() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: { beats })
        #expect(clock.phase(.cover) == .waiting)
        #expect(clock.reveal(.cover, at: base)(.header) == 0)
        clock.isActive = true
        let start = base.addingTimeInterval(IntroClock.lead)
        #expect(clock.phase(.cover) == .playing(start))
        #expect(spy.prepared == 1)
        #expect(spy.played == [.cover])
        // The haptics start with the frames.
        #expect(spy.starts == [start])
        #expect(ledger.contains(october, .cover))
        // A key reads 0 until its beat, while its sequence plays.
        #expect(clock.reveal(.cover, at: start.addingTimeInterval(0.1))(.row(0)) == 0)
        let reveal = clock.reveal(.cover, at: start.addingTimeInterval(0.46))
        #expect(reveal(.header) == 1)
        #expect(abs(reveal(.row(0)) - 0.5) < 1e-9)
        // A key the beats don't name has landed.
        #expect(reveal(.row(1)) == 1)
        clock.advance(to: start.addingTimeInterval(0.5))
        #expect(clock.isPlaying(.cover))
        clock.advance(to: start.addingTimeInterval(0.7))
        #expect(clock.phase(.cover) == .settled)
        #expect(spy.stopped == [.cover])
    }

    @Test("the deck's top card plays; each card shown in turn plays in its turn")
    func showOrder() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        for sequence in [IntroSequence.cover, weeks, .section(.persons)] {
            clock.register(sequence, beats: { beats })
        }
        clock.isActive = true
        // Registered, a card waits until it is on top.
        #expect(clock.phase(.cover) == .waiting)
        clock.show(.cover)
        #expect(clock.isPlaying(.cover))
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.phase(.cover) == .settled)
        // The card beneath never plays on its own.
        #expect(clock.phase(weeks) == .waiting)
        clock.show(weeks)
        #expect(clock.isPlaying(weeks))
        clock.advance(to: base.addingTimeInterval(2))
        clock.show(.section(.persons))
        #expect(spy.played == [.cover, weeks, .section(.persons)])
    }

    @Test("a card thrown off mid-play lands, and the next plays; one brought back stays landed")
    func showLandsPrevious() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.register(.cover, beats: { beats })
        clock.register(weeks, beats: { beats })
        clock.isActive = true
        clock.show(.cover)
        clock.show(weeks)
        #expect(clock.phase(.cover) == .settled)
        #expect(spy.stopped == [.cover])
        #expect(clock.isPlaying(weeks))
        // Swiping back down: the cover is settled for good, and the weeks land.
        clock.show(.cover)
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.phase(weeks) == .settled)
        #expect(spy.played == [.cover, weeks])
        #expect(ledger.contains(october, weeks))
    }

    @Test("a card shown before its beats are registered plays as they arrive, with the latest")
    func registerAfterShow() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        clock.isActive = true
        clock.show(.cover)
        #expect(clock.phase(.cover) == .waiting)
        #expect(spy.played.isEmpty)
        let slow = [IntroBeat(key: .header, at: 0, duration: 2, tick: .light)]
        clock.register(.cover, beats: { slow })
        #expect(clock.isPlaying(.cover))
        #expect(spy.beats == [slow])
        // Registered again while it plays (the host reappeared): the score in play stands.
        clock.register(.cover, beats: { beats })
        #expect(spy.played == [.cover])
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.isPlaying(.cover))
    }

    @Test("a card registered before it is shown plays its beats as it comes on top")
    func registerBeforeShow() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        clock.register(weeks, beats: { beats })
        clock.isActive = true
        #expect(spy.played.isEmpty)
        clock.show(weeks)
        #expect(clock.isPlaying(weeks))
        #expect(spy.beats == [beats])
    }

    @Test("a card with no beats to play lands at once, silent")
    func showEmpty() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        clock.register(weeks, beats: { [] })
        clock.isActive = true
        clock.show(weeks)
        #expect(clock.phase(weeks) == .settled)
        #expect(spy.played.isEmpty)
        #expect(clock.reveal(weeks, at: base)(.header) == 1)
    }

    @Test("left to its own schedule, a sequence lands at its end")
    @MainActor
    func scheduledEnd() async throws {
        let clock = IntroClock(month: october, order: [.cover], ledger: IntroLedger(), haptics: nil)
        clock.enter(.cover, beats: { [IntroBeat(key: .header, at: 0, duration: 0.05)] })
        clock.isActive = true
        #expect(clock.isPlaying(.cover))
        try await Task.sleep(for: .milliseconds(300))
        #expect(clock.phase(.cover) == .settled)
    }

    @Test("one voice at a time, in page order, whatever order they enter in")
    func queue() {
        let clock = clock()
        clock.enter(weeks, beats: { beats })
        clock.enter(.cover, beats: { beats })
        clock.isActive = true
        #expect(clock.isPlaying(.cover))
        #expect(clock.phase(weeks) == .waiting)
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.isPlaying(weeks))
    }

    @Test("a waiting sequence that left the viewport is passed over; a playing one that leaves lands")
    func leaving() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        clock.enter(.cover, beats: { beats })
        clock.enter(weeks, beats: { beats })
        clock.isActive = true
        clock.leave(weeks)
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.phase(weeks) == .waiting)
        #expect(spy.played == [.cover])
        clock.enter(weeks, beats: { beats })
        #expect(clock.isPlaying(weeks))
        clock.leave(weeks)
        #expect(clock.phase(weeks) == .settled)
        #expect(spy.stopped == [.cover, weeks])
    }

    @Test("a tap lands what plays and what waits in view, and counts them as played")
    func skip() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: { beats })
        clock.enter(weeks, beats: { beats })
        clock.isActive = true
        clock.skip()
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.phase(weeks) == .settled)
        #expect(ledger.contains(october, weeks))
        #expect(spy.stopped == [.cover])
        #expect(spy.played == [.cover])
    }

    @Test("a page stops only its own haptics, never another page's")
    func ownHaptics() {
        let spy = HapticsSpy()
        let september = IntroClock(month: CalendarMonth(year: 2026, month: 9), order: [.cover], ledger: IntroLedger(),
                                   haptics: spy, now: { [base] in base }, schedules: false)
        let clock = clock(haptics: spy)
        clock.enter(.cover, beats: { beats })
        september.enter(.cover, beats: { beats })
        clock.isActive = true
        september.isActive = true
        clock.skip()
        #expect(spy.stoppedMonths == [october])
        #expect(september.isPlaying(.cover))
    }

    @Test("a page whose every sequence played this launch never wakes the engine")
    func preparesOnlyWhatPlays() {
        let ledger = IntroLedger()
        for sequence in [IntroSequence.cover, weeks, .section(.persons)] {
            ledger.record(october, sequence)
        }
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.isActive = true
        #expect(spy.prepared == 0)
    }

    @Test("losing focus lands what plays; what waits stays unrecorded and plays on the next focus")
    func focusLoss() {
        let ledger = IntroLedger()
        let clock = clock(ledger)
        clock.enter(.cover, beats: { beats })
        clock.enter(weeks, beats: { beats })
        clock.isActive = true
        clock.isActive = false
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.phase(weeks) == .waiting)
        #expect(!ledger.contains(october, weeks))
        clock.isActive = true
        #expect(clock.isPlaying(weeks))
    }

    @Test("a sequence played earlier this launch draws settled and never plays again")
    func ledgerHolds() {
        let ledger = IntroLedger()
        ledger.record(october, .cover)
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: { beats })
        clock.isActive = true
        #expect(spy.played.isEmpty)
        #expect(clock.reveal(.cover, at: base)(.header) == 1)
    }

    @Test("a sequence plays the beats its closure gives as it starts, not those it had as it entered")
    func freshBeats() {
        var current = beats
        let clock = clock()
        clock.enter(.cover, beats: { current })
        current = [IntroBeat(key: .header, at: 0, duration: 2, tick: .light)]
        clock.isActive = true
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.isPlaying(.cover))
        #expect(abs(clock.reveal(.cover, at: base.addingTimeInterval(1.05))(.header) - 0.5) < 1e-9)
    }

    @Test("a new order plays the sections in it")
    func reorder() {
        let clock = clock()
        clock.order = [weeks, .cover]
        clock.enter(.cover, beats: { beats })
        clock.enter(weeks, beats: { beats })
        clock.isActive = true
        #expect(clock.isPlaying(weeks))
        #expect(clock.phase(.cover) == .waiting)
    }

    @Test("a frozen clock draws its sequence at its instant and ignores the deck")
    func frozen() {
        let clock = IntroClock.frozen(month: october, sequence: .cover, beats: beats, elapsed: 0.46)
        let reveal = clock.reveal(.cover, at: clock.frozenAt ?? .distantPast)
        #expect(abs(reveal(.row(0)) - 0.5) < 1e-9)
        clock.register(weeks, beats: { beats })
        clock.show(weeks)
        #expect(clock.isPlaying(.cover))
        #expect(clock.phase(weeks) == .waiting)
    }
}
