import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the gem: ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta.
@Suite("Intro score: the gem")
struct IntroScoreGemTests {
    @Test("a run that crosses the top sweeps clockwise through it, from after its widest gap")
    func sweep() {
        let facets: [(id: String, theta: Double)] = [("kite_0", 0), ("girdle_1", 0.25), ("star_6", 6.5),
                                                     ("girdle_0", -0.25)]
        #expect(IntroScore.sweep(facets) == ["star_6", "girdle_0", "kite_0", "girdle_1"])
        #expect(IntroScore.sweep([("kite_3", 3)]) == ["kite_3"])
    }

    @Test("the families that own facets, in the lead order, each with exactly its facets")
    func runs() {
        let facets: [String: String?] = ["kite_0": "joy", "star_0": "joy", "kite_3": "calm", "star_3": nil]
        let runs = IntroScore.gemRuns(order: ["calm", "anger", "joy"], facets: facets)
        #expect(runs.map(\.slug) == ["calm", "joy"])
        #expect(runs[0].facets == ["kite_3"])
        // Swept clockwise: kite_0 sits at theta 0, star_0 at 0.5.
        #expect(runs[1].facets == ["kite_0", "star_0"])
    }

    @Test("the count first, then facets 45 ms apart, a pause between families, each name with its first facet")
    func beats() throws {
        let runs = [IntroGemRun(slug: "joy", facets: ["a", "b", "c"]), IntroGemRun(slug: "calm", facets: ["d", "e"])]
        let beats = IntroScore.gem(runs: runs, pebbles: 12)
        func at(_ key: IntroKey) throws -> TimeInterval { try #require(beats.first { $0.key == key }).at }
        #expect(try at(.lead) > at(.header))
        #expect(try at(.blank) < at(.count))
        let count = try #require(beats.first { $0.key == .count })
        #expect(abs(try at(.facet("a")) - (count.at + count.duration + IntroScore.countAfter)) < 1e-12)
        #expect(abs(try at(.facet("b")) - at(.facet("a")) - IntroScore.facetGap) < 1e-12)
        #expect(abs(try at(.facet("d")) - at(.facet("c")) - IntroScore.facetGap - IntroScore.familyPause) < 1e-12)
        #expect(try at(.label("joy")) == at(.facet("a")))
        #expect(try at(.label("calm")) == at(.facet("d")))
        #expect(try at(.more) == at(.facet("e")))
        #expect(beats.filter { if case .facet = $0.key { return true } else { return false } }
            .allSatisfy { $0.tick == .facet })
    }

    @Test("the count rises to the month's pebbles slow-fast-slow, a tick per pebble, landing firm")
    func countUp() throws {
        let beats = IntroScore.gem(runs: [], pebbles: 24)
        let count = try #require(beats.first { $0.key == .count })
        #expect(count.at == IntroScore.countAt)
        #expect(count.duration == IntroScore.countSpan(24))
        let tallies = (1...24).compactMap { tally in beats.first { $0.key == .tally(tally) } }
        #expect(tallies.count == 24)
        #expect(tallies.dropLast().allSatisfy { $0.tick == .light })
        #expect(tallies.last?.tick == .firm)
        #expect(abs((tallies.last?.at ?? 0) - (count.at + count.duration)) < 1e-12)
        // Accelerating, then landing slowly: the middle ticks are closer than the first and the last.
        let gaps = zip(tallies.dropFirst(), tallies).map { $0.at - $1.at }
        #expect(gaps[11] < gaps[0])
        #expect(gaps[11] < gaps[gaps.count - 1])
        // Each tick is the moment the shown count reaches its pebble.
        for tally in tallies {
            guard case let .tally(pebble) = tally.key else { continue }
            #expect(IntroScore.counted(24, at: (tally.at - count.at) / count.duration) == pebble)
        }
        #expect(IntroScore.counted(24, at: 0) == 0)
    }

    @Test("a fuller month counts for longer, within bounds; an empty one has no tally")
    func countSpan() {
        #expect(IntroScore.countSpan(0) == 0.6)
        #expect(IntroScore.countSpan(50) > IntroScore.countSpan(10))
        #expect(IntroScore.countSpan(1000) == 1.2)
        #expect(!IntroScore.gem(runs: [], pebbles: 0).contains { if case .tally = $0.key { true } else { false } })
    }
}
