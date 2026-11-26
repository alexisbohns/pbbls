import Foundation
import Testing
@testable import Pebbles

@MainActor
private final class SpyHaptics: StoneCeremonyHapticPlaying {
    var plays: [(events: [IntroHapticEvent], begin: Date)] = []
    var stops = 0
    var prepares = 0

    func prepare() { prepares += 1 }
    func play(_ events: [IntroHapticEvent], startingAt begin: Date) { plays.append((events, begin)) }
    func stop() { stops += 1 }
}

@MainActor
@Suite("Stone ceremony haptic driver")
struct StoneCeremonyDriverTests {
    private let t0 = Date(timeIntervalSinceReferenceDate: 1_000)

    private func driver(_ valence: Valence = .neutralMedium, frozen: StoneCeremonyFreeze? = nil, spy: SpyHaptics,
                        hapticsOn: Bool = true) -> StoneCeremonyDriver {
        let timeline = StoneCeremonyTimeline(valence: valence, carves: true, mode: .full)
        let ceremony = frozen.map {
            StoneCeremony(timeline: timeline, markedDay: 2, heroReach: [2.388, 2.245], frozen: $0)
        } ?? StoneCeremony(timeline: timeline, markedDay: 2, heroReach: [2.388, 2.245], start: t0)
        return StoneCeremonyDriver(ceremony: ceremony, carveLength: 300, hapticsOn: hapticsOn, haptics: spy)
    }

    @Test func startPlaysAct1() {
        let spy = SpyHaptics()
        driver(spy: spy).start(at: t0)
        #expect(spy.plays.count == 1)
        #expect(spy.plays[0].begin == t0)
        #expect(spy.plays[0].events.first?.at == 0.32)
    }

    @Test func skipIsSilent() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        d.tapOutside(at: t0.addingTimeInterval(0.5))
        #expect(spy.stops == 1)
        #expect(spy.plays.count == 1)
    }

    @Test func continueMidCarve() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        let tap = t0.addingTimeInterval(1)
        d.tapContinue(at: tap)
        #expect(spy.stops == 1)
        #expect(spy.plays.count == 2)
        #expect(spy.plays[1].begin == tap)
        #expect(spy.plays[1].events.count == 2)
        // A second Continue only skips: it plays nothing.
        d.tapContinue(at: tap.addingTimeInterval(0.1))
        #expect(spy.plays.count == 2)
    }

    @Test func closeStops() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        d.tapContinue(at: t0.addingTimeInterval(1))
        let before = spy.stops
        d.close()
        #expect(spy.stops == before + 1)
    }

    @Test func quietPlaysNothing() {
        let spy = SpyHaptics()
        let d = driver(.lowlightLarge, spy: spy)
        d.start(at: t0)
        d.tapContinue(at: t0.addingTimeInterval(1))
        #expect(spy.plays.isEmpty)
    }

    @Test func hapticsOffPlaysNothing() {
        let spy = SpyHaptics()
        let d = driver(spy: spy, hapticsOn: false)
        d.start(at: t0)
        d.tapContinue(at: t0.addingTimeInterval(1))
        #expect(spy.plays.isEmpty)
    }

    @Test func frozenPlaysNothing() {
        let spy = SpyHaptics()
        driver(frozen: .pause, spy: spy).start(at: t0)
        driver(frozen: .settled, spy: spy).start(at: t0)
        #expect(spy.plays.isEmpty)
    }

    @Test func closeDuringAct1() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        let before = spy.stops
        d.close()
        #expect(spy.stops == before + 1)
    }

    @Test func closeLatches() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        d.close()
        let plays = spy.plays.count
        let prepares = spy.prepares
        d.tapContinue(at: t0.addingTimeInterval(1))
        d.tapOutside(at: t0.addingTimeInterval(1.1))
        d.tick(at: t0.addingTimeInterval(10))
        d.start(at: t0.addingTimeInterval(11))
        #expect(spy.plays.count == plays)
        #expect(spy.prepares == prepares)
    }

    @Test func frozenNeverPlays() {
        let spy = SpyHaptics()
        let d = driver(frozen: .pause, spy: spy)
        d.tapContinue(at: t0)
        #expect(spy.plays.isEmpty)
    }

    @Test func preparesAtThePause() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        let started = spy.prepares
        d.tick(at: t0.addingTimeInterval(0.5))
        #expect(spy.prepares == started)
        d.tick(at: t0.addingTimeInterval(10))
        #expect(d.ceremony.phase == .paused)
        #expect(spy.prepares == started + 1)
        // Further ticks at rest do not warm again.
        d.tick(at: t0.addingTimeInterval(11))
        #expect(spy.prepares == started + 1)
    }

    @Test func preparesWhenASkipReachesThePause() {
        let spy = SpyHaptics()
        let d = driver(spy: spy)
        d.start(at: t0)
        let started = spy.prepares
        d.tapOutside(at: t0.addingTimeInterval(0.5))
        #expect(d.ceremony.phase == .paused)
        #expect(spy.prepares == started + 1)
    }

    @Test func noPrepareAtThePauseWhenNothingPlays() {
        let off = SpyHaptics()
        let d1 = driver(spy: off, hapticsOn: false)
        d1.start(at: t0)
        d1.tick(at: t0.addingTimeInterval(10))
        #expect(d1.ceremony.phase == .paused)
        #expect(off.prepares == 0)

        let quiet = SpyHaptics()
        let d2 = driver(.lowlightLarge, spy: quiet)
        d2.start(at: t0)
        d2.tick(at: t0.addingTimeInterval(10))
        #expect(d2.ceremony.phase == .paused)
        #expect(quiet.prepares == 0)
    }
}
