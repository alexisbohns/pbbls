import Foundation
import Testing
@testable import Pebbles

/// #1241 D5: what the intro asks of the Taptic Engine.
@Suite("Intro haptics")
struct IntroHapticsTests {
    @Test("each ticking beat plays at its time; one closer than 25 ms to the last kept is dropped")
    func floor() {
        let beats = [
            IntroBeat(key: .row(1), at: 0.05, duration: 0.28, tick: .facet),
            IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
            IntroBeat(key: .lead, at: 0.01, duration: 0.28),
            IntroBeat(key: .row(0), at: 0.02, duration: 0.28, tick: .light)
        ]
        #expect(IntroHaptics.events(for: beats) == [IntroHapticEvent(at: 0, tick: .light),
                                                    IntroHapticEvent(at: 0.05, tick: .facet)])
    }

    @Test("a 31-day garland keeps its name, every day and its leaf")
    func garland() {
        let beats = IntroScore.cover(days: [], daysInMonth: 31, numbered: [], souls: 0, lines: [])
        #expect(IntroHaptics.events(for: beats).count == 33)
    }

    @Test("a 31-day garland with two pebbles on every day still ticks every day, its name and its leaf")
    func stackedGarland() throws {
        let pebble = { (intensity: Int) in
            CoverPebble(momentId: "m", emotionId: nil, intensity: intensity, polarity: .highlight)
        }
        let days = (0..<31).map { CoverDay(day: $0, pebbles: [pebble(1), pebble(3)]) }
        let beats = IntroScore.cover(days: days, daysInMonth: 31, numbered: [], souls: 0, lines: [])
        let ticked = Set(IntroHaptics.events(for: beats).map(\.at))
        for key in (0..<31).map(IntroKey.slot) + [.monthName, .leaf] {
            let beat = try #require(beats.first { $0.key == key })
            #expect(ticked.contains(beat.at), "\(key) lost its tick")
        }
    }

    @Test("a swelling beat plays as a swell over its duration, never as a tick; its ticks stay ticks")
    func swells() throws {
        let beats = IntroScore.domains(shares: [1, 0.25], more: false)
        let swells = IntroHaptics.swells(for: beats)
        let gauges = try [0, 1].map { row in try #require(beats.first { $0.key == .gauge(row) }) }
        #expect(swells.map(\.at) == gauges.map(\.at))
        #expect(swells.map(\.duration) == gauges.map(\.duration))
        #expect(swells[0].swell.to > swells[1].swell.to)
        let ticks = IntroHaptics.events(for: beats).map(\.at)
        #expect(!ticks.contains(gauges[0].at))
        #expect(try ticks.contains(#require(beats.first { $0.key == .full(0) }).at))
        // A beat with nothing to swell over plays no swell.
        let still = IntroBeat(key: .gauge(0), at: 0, duration: 0, swell: IntroSwell(from: 0, to: 1, sharpness: 0))
        #expect(IntroHaptics.swells(for: [still]).isEmpty)
    }

    @Test("a swell falls silent a floor before its gauge is full, and the full tick plays at its own strength")
    func swellControl() throws {
        let beats = IntroScore.domains(shares: [0.25], more: false)
        let swell = try #require(IntroHaptics.swells(for: beats).first)
        let full = try #require(beats.first { $0.key == .full(0) })
        #expect(abs(swell.at + swell.duration - full.at) < 1e-9)
        #expect(abs(swell.duration - swell.sounding - IntroHaptics.floor) < 1e-9)
        #expect(swell.control.map { $0.at } == [0, swell.sounding, swell.duration])
        #expect(swell.control.map { $0.value } == [swell.swell.from, swell.swell.to, 1])
    }
}
