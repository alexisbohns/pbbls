import Foundation
import Testing
@testable import Pebbles

/// #1241 D10: each card's signature, one row at a time, each in its parts.
@Suite("Intro score: the signatures")
struct IntroScoreSignaturesTests {
    private func beat(_ beats: [IntroBeat], _ key: IntroKey) throws -> IntroBeat {
        try #require(beats.first { $0.key == key })
    }

    @Test("a week's column: its date, its days down the column, a tick per pebble, none for a slot, then its cairn")
    func weekColumn() throws {
        let beats = IntroScore.weekColumns([IntroWeekColumn(days: [nil, 0, 2, nil, nil, nil, nil], stones: 2),
                                            IntroWeekColumn(days: [0, 0, 0, 0, 0, 0, 1], stones: 1)])
        #expect(try beat(beats, .header).tick == .arrive)
        let date = try beat(beats, .words(0))
        #expect(date.at == IntroScore.rowsAt)
        #expect(date.tick == .light)
        // A day outside the month has no beat; an empty day lands silently.
        #expect(!beats.contains { $0.key == .rest(0, 0) })
        let rest = try beat(beats, .rest(0, 1))
        #expect(abs(rest.at - date.at - IntroScore.partGap) < 1e-12)
        #expect(rest.tick == nil)
        let first = try beat(beats, .pip(0, 2, 0))
        let second = try beat(beats, .pip(0, 2, 1))
        #expect(abs(first.at - rest.at - IntroScore.dayGap) < 1e-12)
        #expect(abs(second.at - first.at - IntroScore.pipGap) < 1e-12)
        #expect(first.tick == .light && second.tick == .light)
        let stone = try beat(beats, .cairn(0, 0))
        #expect(abs(stone.at - second.at - IntroScore.cairnAfter) < 1e-12)
        #expect(try beat(beats, .cairn(0, 1)).tick == .cairn(1))
        #expect(abs(try beat(beats, .words(1)).at - beat(beats, .cairn(0, 1)).at - IntroScore.nextWeek) < 1e-12)
    }

    @Test("an empty week's column ends on its dotted stone, on a faint tick")
    func emptyWeekColumn() throws {
        let beats = IntroScore.weekColumns([IntroWeekColumn(days: [0, 0, 0, 0, 0, 0, 0], stones: 0)])
        #expect(try beat(beats, .cairn(0, 0)).tick == .faint)
        #expect(!beats.contains { if case .pip = $0.key { true } else { false } })
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
