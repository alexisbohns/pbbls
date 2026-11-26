import Foundation
import Testing
@testable import Pebbles

@Suite("Stone ceremony state machine")
struct StoneCeremonyTests {
    private let timeline = StoneCeremonyTimeline(valence: .neutralMedium, carves: true, mode: .full)
    private let reach = [2.388, 2.245]
    private let t0 = Date(timeIntervalSinceReferenceDate: 1_000)

    private func ceremony(start: Date? = nil) -> StoneCeremony {
        StoneCeremony(timeline: timeline, markedDay: 2, heroReach: reach, start: start ?? t0)
    }

    /// A ceremony already in Act 2 since `t1`.
    private func flying(since t1: Date) -> StoneCeremony {
        var c = ceremony()
        let started1 = c.tapContinue(at: t1)
        #expect(started1)
        return c
    }

    @Test func continueGate() {
        var c = ceremony()
        let started2 = c.tapContinue(at: t0.addingTimeInterval(0.1))
        #expect(!started2)
        #expect(c.phase == .paused)
    }

    @Test func skipToPause() {
        var c = ceremony()
        c.tapOutside(at: t0.addingTimeInterval(0.5))
        #expect(c.phase == .paused)
        #expect(c.frame(at: t0.addingTimeInterval(0.5)) == timeline.act1(at: timeline.pause, heroReach: reach))
    }

    @Test func earlyContinue() {
        var c = ceremony()
        let tap = t0.addingTimeInterval(1.0)
        let started3 = c.tapContinue(at: tap)
        #expect(started3)
        #expect(c.phase == .toItsDay(since: tap))
        let f = c.frame(at: tap)
        #expect(f.lipTrim == 1)
        #expect(f.inkTrim == 1)
        #expect(f.inkOpacity == 1)
        #expect(f == timeline.act2(at: 0, markedDay: 2))
    }

    @Test func continueFromThePause() {
        var c = ceremony()
        c.tapOutside(at: t0.addingTimeInterval(0.5))
        let tap = t0.addingTimeInterval(3)
        let started4 = c.tapContinue(at: tap)
        #expect(started4)
        #expect(c.phase == .toItsDay(since: tap))
    }

    @Test func doubleTap() {
        var c = ceremony()
        let started5 = c.tapContinue(at: t0.addingTimeInterval(2))
        #expect(started5)
        let started6 = c.tapContinue(at: t0.addingTimeInterval(2.1))
        #expect(!started6)
        #expect(c.phase == .settled)
    }

    @Test func tapOutsideDuringAct2() {
        var c = ceremony()
        let started7 = c.tapContinue(at: t0.addingTimeInterval(1))
        #expect(started7)
        c.tapOutside(at: t0.addingTimeInterval(1.2))
        #expect(c.phase == .settled)
        c.tapOutside(at: t0.addingTimeInterval(5))
        #expect(c.phase == .settled)
    }

    @Test func ticks() {
        var c = ceremony()
        c.tick(at: t0.addingTimeInterval(1.9))
        #expect(c.phase == .setDown(since: t0))
        c.tick(at: t0.addingTimeInterval(1.97))
        #expect(c.phase == .paused)

        let t1 = t0.addingTimeInterval(10)
        var d = flying(since: t1)
        d.tick(at: t1.addingTimeInterval(1.17))
        #expect(d.phase == .toItsDay(since: t1))
        d.tick(at: t1.addingTimeInterval(1.47))
        #expect(d.phase == .settled)

        var p = ceremony()
        p.tapOutside(at: t0)
        p.tick(at: .distantFuture)
        #expect(p.phase == .paused)
    }

    @Test func richerInputOnlySettled() {
        let t1 = t0.addingTimeInterval(10)
        var c = ceremony()
        #expect(c.acceptsRicherInput(at: t0.addingTimeInterval(5)) == false)
        c.tapOutside(at: t0)
        #expect(c.acceptsRicherInput(at: t0.addingTimeInterval(5)) == false)
        let started8 = c.tapContinue(at: t1)
        #expect(started8)
        #expect(c.acceptsRicherInput(at: t1.addingTimeInterval(1.0)) == false)
        #expect(c.acceptsRicherInput(at: t1.addingTimeInterval(1.2)))
        c.tapOutside(at: t1.addingTimeInterval(0.5))
        #expect(c.acceptsRicherInput(at: t1.addingTimeInterval(0.5)))
    }

    @Test func isRunning() {
        var c = ceremony()
        #expect(c.isRunning)
        let started9 = c.tapContinue(at: t0.addingTimeInterval(1))
        #expect(started9)
        #expect(c.isRunning)
        c.tapOutside(at: t0.addingTimeInterval(1.1))
        #expect(c.isRunning == false)
        var p = ceremony()
        p.tapOutside(at: t0)
        #expect(p.isRunning == false)
    }

    @Test func endsAt() {
        var c = ceremony()
        #expect(c.endsAt() == t0.addingTimeInterval(timeline.pause))  // the pause, 1.96 s from the start
        #expect(abs(timeline.pause - 1.96) < 1e-9)
        let t1 = t0.addingTimeInterval(10)
        let d = flying(since: t1)
        #expect(d.endsAt() == t1.addingTimeInterval(timeline.still))
        c.tapOutside(at: t0)
        #expect(c.endsAt() == nil)
        var s = d
        s.tapOutside(at: t1.addingTimeInterval(0.1))
        #expect(s.endsAt() == nil)
    }

    @Test func frozen() {
        let p = StoneCeremony(timeline: timeline, markedDay: 2, heroReach: reach, frozen: .pause)
        #expect(p.phase == .paused)
        #expect(p.isRunning == false)
        #expect(p.frame(at: t0) == timeline.act1(at: timeline.pause, heroReach: reach))
        let s = StoneCeremony(timeline: timeline, markedDay: 2, heroReach: reach, frozen: .settled)
        #expect(s.phase == .settled)
        #expect(s.isRunning == false)
        #expect(s.frame(at: t0) == timeline.settledFrame(markedDay: 2))
    }
}
