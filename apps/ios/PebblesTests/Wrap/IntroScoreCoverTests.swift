import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the cover.
@Suite("Intro score: the cover")
struct IntroScoreCoverTests {
    private func pebble(_ intensity: Int) -> CoverPebble {
        CoverPebble(momentId: "m", emotionId: nil, intensity: intensity, polarity: .highlight)
    }

    @Test("one beat per day from the 1st: an empty day faint, a seal by its intensity, a stacked seal a step later")
    func days() throws {
        let days = [CoverDay(day: 0, pebbles: [pebble(3)]), CoverDay(day: 4, pebbles: [pebble(1), pebble(2)])]
        let beats = IntroScore.cover(days: days, daysInMonth: 31, numbered: [0, 4, 30], souls: 0, lines: [])
        let slots = (0..<31).compactMap { day in beats.first { $0.key == .slot(day) } }
        #expect(slots.count == 31)
        let times = slots.map(\.at)
        #expect(zip(times.dropFirst(), times).allSatisfy { $0 > $1 })
        #expect(times.first == IntroScore.garlandAt)
        #expect(slots[0].tick == .seal(3))
        #expect(slots[1].tick == .faint)
        #expect(slots[4].tick == .seal(1))
        let stacked = try #require(beats.first { $0.key == .seal(4, 1) })
        #expect(abs(stacked.at - slots[4].at - IntroScore.stackGap) < 1e-12)
        #expect(stacked.tick == .seal(2))
        let numerals = beats.compactMap { beat -> Int? in
            if case let .numeral(day) = beat.key { return day }
            return nil
        }
        #expect(numerals == [0, 4, 30])
        #expect(beats.first { $0.key == .numeral(4) }?.at == slots[4].at)
    }

    @Test("the name before the garland; the leaf ties it; then the souls and the lines, in order")
    func order() throws {
        let beats = IntroScore.cover(days: [], daysInMonth: 30, numbered: [], souls: 2, lines: [.summary, .colophon])
        let name = try #require(beats.first { $0.key == .monthName })
        #expect(name.tick == .light)
        #expect(name.at < IntroScore.garlandAt)
        let leaf = try #require(beats.first { $0.key == .leaf })
        let lastSlot = try #require(beats.first { $0.key == .slot(29) })
        #expect(leaf.tick == .knot)
        #expect(leaf.at > lastSlot.at)
        #expect(beats.filter { $0.at > leaf.at }.map(\.key) == [.soul(0), .soul(1), .summary, .colophon])
        #expect(!beats.contains { $0.key == .names })
        let last = try #require(beats.last)
        #expect(IntroScore.duration(beats) == last.at + IntroMotion.rise)
    }
}
