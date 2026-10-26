import Foundation
import Testing
@testable import Pebbles

/// #1241 D10: each card's signature, one row at a time, each in its parts.
@Suite("Intro score: the signatures")
struct IntroScoreSignaturesTests {
    private func beat(_ beats: [IntroBeat], _ key: IntroKey) throws -> IntroBeat {
        try #require(beats.first { $0.key == key })
    }

    @Test("a week: its cairn stone by stone, rising firmer, then its words, then its souls, then the next week")
    func week() throws {
        let beats = IntroScore.weeks([IntroWeekShape(stones: 3, souls: 2), IntroWeekShape(stones: 1, souls: 0)])
        #expect(try beat(beats, .header).tick == .arrive)
        let stones = try (0..<3).map { try beat(beats, .cairn(0, $0)) }
        #expect(stones[0].at == IntroScore.rowsAt)
        #expect(abs(stones[2].at - stones[0].at - 2 * IntroScore.stoneGap) < 1e-12)
        #expect(stones.map(\.tick) == [.cairn(0), .cairn(1), .cairn(2)])
        #expect(try (stones[0].tick?.intensity ?? 1) < (stones[2].tick?.intensity ?? 0))
        let words = try beat(beats, .words(0))
        #expect(abs(words.at - stones[2].at - IntroScore.wordsAfter) < 1e-12)
        #expect(words.tick == .light)
        let first = try beat(beats, .mark(0, 0))
        let second = try beat(beats, .mark(0, 1))
        #expect(abs(first.at - words.at - IntroScore.partGap) < 1e-12)
        #expect(abs(second.at - first.at - IntroScore.markGap) < 1e-12)
        #expect(abs(try beat(beats, .cairn(1, 0)).at - second.at - IntroScore.nextWeek) < 1e-12)
        // A week without souls goes on from its words.
        #expect(!beats.contains { $0.key == .mark(1, 0) })
    }

    @Test("an empty week lands whole on a faint tick, and the next week follows")
    func emptyWeek() throws {
        let beats = IntroScore.weeks([IntroWeekShape(stones: 0, souls: 0), IntroWeekShape(stones: 2, souls: 1)])
        let empty = try beat(beats, .row(0))
        #expect(empty.at == IntroScore.rowsAt)
        #expect(empty.tick == .faint)
        #expect(!beats.contains { $0.key == .cairn(0, 0) || $0.key == .words(0) })
        #expect(abs(try beat(beats, .cairn(1, 0)).at - empty.at - IntroScore.nextWeek) < 1e-12)
    }

    @Test("a milestone stamps, ticking at impact as firmly as it is intense, then its date, name and souls")
    func milestone() throws {
        let beats = IntroScore.milestones([IntroMilestoneShape(intensity: 3, souls: 1),
                                           IntroMilestoneShape(intensity: 1, souls: 0)])
        let stamp = try beat(beats, .stamp(0))
        #expect(stamp.at == IntroScore.rowsAt)
        #expect(stamp.duration == IntroScore.stampSpan)
        #expect(stamp.tick == nil)
        let impact = try beat(beats, .impact(0))
        #expect(abs(impact.at - stamp.at - IntroMotion.stampImpact * IntroScore.stampSpan) < 1e-12)
        #expect(impact.tick == .seal(3))
        let date = try beat(beats, .date(0))
        let name = try beat(beats, .name(0))
        #expect(abs(date.at - stamp.at - IntroScore.stampSpan) < 1e-12)
        #expect(abs(name.at - date.at - IntroScore.partGap) < 1e-12)
        let soul = try beat(beats, .mark(0, 0))
        #expect(abs(soul.at - name.at - IntroScore.partGap) < 1e-12)
        #expect(abs(try beat(beats, .stamp(1)).at - soul.at - IntroScore.nextMilestone) < 1e-12)
        #expect(try beat(beats, .impact(1)).tick == .seal(1))
    }

    @Test("a domain: its name, then its gauge swelling for longer the fuller it is, a tick as it stops")
    func domains() throws {
        let beats = IntroScore.domains(shares: [1, 0.5], more: true)
        let row = try beat(beats, .row(0))
        #expect(row.at == IntroScore.rowsAt)
        #expect(row.tick == .light)
        let gauge = try beat(beats, .gauge(0))
        #expect(abs(gauge.at - row.at - IntroScore.partGap) < 1e-12)
        #expect(gauge.duration == IntroScore.fillSpan(1))
        #expect(gauge.tick == nil)
        #expect(gauge.swell == IntroSwell(from: IntroScore.swellFrom, to: IntroScore.swellTo(1),
                                          sharpness: IntroScore.swellSharpness))
        let full = try beat(beats, .full(0))
        #expect(abs(full.at - gauge.at - gauge.duration) < 1e-12)
        #expect(full.tick == .light)
        #expect(abs(try beat(beats, .row(1)).at - full.at - IntroScore.nextRow) < 1e-12)
        #expect(IntroScore.fillSpan(0.5) < IntroScore.fillSpan(1))
        #expect(IntroScore.swellTo(0.5) < IntroScore.swellTo(1))
        #expect(try beat(beats, .more).at == beat(beats, .full(1)).at)
        #expect(!IntroScore.domains(shares: [1], more: false).contains { $0.key == .more })
    }

    @Test("a stamp comes in large and clear, presses past its size at impact, and settles at its size")
    func stampCurves() {
        #expect(IntroMotion.stampScale(0) == 1.25)
        #expect(IntroMotion.stampScale(IntroMotion.stampImpact) < 1)
        #expect(abs(IntroMotion.stampScale(1) - 1) < 1e-12)
        #expect(IntroMotion.stampOpacity(0) == 0)
        #expect(IntroMotion.stampOpacity(IntroMotion.stampImpact) == 1)
    }

    @Test("tempo scales a swell's beat with its swell")
    func scaledSwell() {
        let swell = IntroSwell(from: 0.1, to: 0.5, sharpness: 0.3)
        let beat = IntroBeat(key: .gauge(0), at: 1, duration: 0.4, swell: swell).scaled(by: 2.5)
        #expect(beat.duration == 1)
        #expect(beat.swell == swell)
    }
}
