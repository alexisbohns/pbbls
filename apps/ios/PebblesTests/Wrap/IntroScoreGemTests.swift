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

    @Test("facets 45 ms apart, a pause between families, each name with its first facet, the count last")
    func beats() throws {
        let runs = [IntroGemRun(slug: "joy", facets: ["a", "b", "c"]), IntroGemRun(slug: "calm", facets: ["d", "e"])]
        let beats = IntroScore.gem(runs: runs)
        func at(_ key: IntroKey) throws -> TimeInterval { try #require(beats.first { $0.key == key }).at }
        #expect(try at(.lead) > at(.header))
        #expect(try at(.blank) < at(.facet("a")))
        #expect(abs(try at(.facet("b")) - at(.facet("a")) - IntroScore.facetGap) < 1e-12)
        #expect(abs(try at(.facet("d")) - at(.facet("c")) - IntroScore.facetGap - IntroScore.familyPause) < 1e-12)
        #expect(try at(.label("joy")) == at(.facet("a")))
        #expect(try at(.label("calm")) == at(.facet("d")))
        let count = try #require(beats.first { $0.key == .count })
        #expect(count.tick == .firm)
        #expect(abs(try count.at - at(.facet("e")) - IntroScore.countAfter) < 1e-12)
        #expect(beats.filter { if case .facet = $0.key { return true } else { return false } }
            .allSatisfy { $0.tick == .facet })
    }
}
