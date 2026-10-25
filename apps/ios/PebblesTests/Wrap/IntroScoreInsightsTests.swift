import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, tone and hours.
@Suite("Intro score: tone and hours")
struct IntroScoreInsightsTests {
    @Test("the tone sweeps once, ticking only on days with pebbles, as the sweep reaches them")
    func tone() throws {
        let days = [ToneDay(up: 1, even: 0, down: 0), ToneDay(up: 0, even: 0, down: 0),
                    ToneDay(up: 0, even: 2, down: 0)]
        let beats = IntroScore.tone(days: days, insights: false)
        let sweep = try #require(beats.first { $0.key == .sweep })
        #expect(sweep.at == IntroScore.sweepAt)
        #expect(sweep.duration == IntroScore.sweepSpan)
        #expect(beats.first { $0.key == .day(0) }?.at == sweep.at)
        #expect(!beats.contains { $0.key == .day(1) })
        let last = try #require(beats.first { $0.key == .day(2) })
        #expect(abs(last.at - (sweep.at + sweep.duration)) < 1e-12)
        #expect(last.tick == .light)
    }

    @Test("leading the insights, the head comes first and the rest shifts")
    func insights() throws {
        let beats = IntroScore.tone(days: [ToneDay(up: 1, even: 0, down: 0)], insights: true)
        #expect(beats.first?.key == .insights)
        let header = try #require(beats.first { $0.key == .header })
        #expect(header.at == IntroScore.insightsShift)
        let sweep = try #require(beats.first { $0.key == .sweep })
        #expect(abs(sweep.at - IntroScore.insightsShift - IntroScore.sweepAt) < 1e-12)
    }

    @Test("the hours: the dial, then one stone per mark, slow-fast-slow")
    func hours() {
        let beats = IntroScore.hours(stones: 4, insights: false)
        let stones = (0..<4).compactMap { rank in beats.first { $0.key == .stone(rank) } }
        #expect(stones.count == 4)
        #expect(stones.first?.at == IntroScore.stonesAt)
        #expect(zip(stones.dropFirst(), stones).allSatisfy { $0.at > $1.at })
        #expect(beats.contains { $0.key == .dial })
    }

    @Test("a stone's rank is its time of day from midnight; ties keep their order")
    func ranks() {
        #expect(IntroScore.ranks([23.5, 0.25, 12, 0.25]) == [3, 0, 2, 1])
    }
}
