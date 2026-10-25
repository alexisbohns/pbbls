import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// A stand-in that records what the clock asked of the haptics.
@MainActor
private final class HapticsSpy: IntroHapticPlaying {
    var prepared = 0
    var played: [IntroSequence] = []
    var stopped: [IntroSequence] = []
    var stoppedAll = 0

    func prepare() { prepared += 1 }
    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval) { played.append(sequence) }
    func stop(_ sequence: IntroSequence) { stopped.append(sequence) }
    func stopAll() { stoppedAll += 1 }
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

    private func clock(_ ledger: IntroLedger? = nil, haptics: HapticsSpy? = nil) -> IntroClock {
        let now = base
        return IntroClock(month: october, order: [.cover, weeks, .section(.persons)], ledger: ledger ?? IntroLedger(),
                          haptics: haptics, now: { now }, schedules: false)
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
        #expect(ledger.contains(october, .cover))
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
        #expect(spy.stoppedAll == 1)
        #expect(spy.played == [.cover])
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

    @Test("a sequence that can't measure its page lands at once, unrecorded, and never blanks again")
    func unmeasured() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        let frame = CGRect(x: 0, y: 0, width: 10, height: 100)
        clock.isActive = true
        clock.report(.cover, beats: { beats }, frame: frame, viewport: nil)
        #expect(clock.phase(.cover) == .settled)
        #expect(!ledger.contains(october, .cover))
        clock.report(.cover, beats: { beats }, frame: frame, viewport: 800)
        #expect(clock.phase(.cover) == .settled)
        #expect(spy.played.isEmpty)
        // One that plays lands too, and the next starts.
        clock.report(weeks, beats: { beats }, frame: frame, viewport: 800)
        clock.report(.section(.persons), beats: { beats }, frame: frame, viewport: 800)
        #expect(clock.isPlaying(weeks))
        clock.report(weeks, beats: { beats }, frame: frame, viewport: nil)
        #expect(clock.phase(weeks) == .settled)
        #expect(spy.stopped == [weeks])
        #expect(clock.isPlaying(.section(.persons)))
    }

    @Test("a frozen clock draws its sequence at its instant and ignores reports")
    func frozen() {
        let clock = IntroClock.frozen(month: october, sequence: .cover, beats: beats, elapsed: 0.46)
        let reveal = clock.reveal(.cover, at: clock.frozenAt ?? .distantPast)
        #expect(abs(reveal(.row(0)) - 0.5) < 1e-9)
        clock.report(.cover, beats: { [] }, frame: CGRect(x: 0, y: -500, width: 10, height: 10), viewport: 800)
        #expect(clock.isPlaying(.cover))
    }
}
