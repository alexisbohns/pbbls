import Foundation
import Testing
@testable import Pebbles

@Suite("RippleSummary decoding")
struct RippleSummaryDecodingTests {

    @Test("decodes the canonical v_ripple row shape")
    func decodesCanonicalShape() throws {
        let json = #"""
        {
          "ripple_level": 3,
          "pebbles_28d": 11,
          "active_today": true
        }
        """#.data(using: .utf8)!

        let summary = try JSONDecoder().decode(RippleSummary.self, from: json)

        #expect(summary.rippleLevel == 3)
        #expect(summary.pebbles28d == 11)
        #expect(summary.activeToday == true)
    }

    @Test("decodes a zero/false row (resting state)")
    func decodesRestingState() throws {
        let json = #"""
        {
          "ripple_level": 0,
          "pebbles_28d": 0,
          "active_today": false
        }
        """#.data(using: .utf8)!

        let summary = try JSONDecoder().decode(RippleSummary.self, from: json)

        #expect(summary.rippleLevel == 0)
        #expect(summary.pebbles28d == 0)
        #expect(summary.activeToday == false)
    }
}

@Suite("RippleSummary level progression")
struct RippleSummaryLevelProgressionTests {

    @Test("pebbles to next level at every level")
    func pebblesToNextLevelTable() {
        let cases: [(level: Int, p28d: Int, expectedRemaining: Int?, expectedNext: Int?)] = [
            (0, 0,  1,   1),  // need 1 pebble to reach level 1
            (1, 1,  4,   2),  // 5 - 1 = 4 to reach level 2
            (1, 4,  1,   2),
            (2, 5,  4,   3),
            (2, 8,  1,   3),
            (3, 12, 1,   4),
            (4, 16, 1,   5),
            (5, 20, 1,   6),
            (6, 21, nil, nil),
            (6, 99, nil, nil)
        ]
        for c in cases {
            let summary = RippleSummary(rippleLevel: c.level, pebbles28d: c.p28d, activeToday: false)
            #expect(summary.pebblesToNextLevel == c.expectedRemaining,
                    "level=\(c.level) p28d=\(c.p28d)")
            #expect(summary.nextLevel == c.expectedNext,
                    "level=\(c.level) p28d=\(c.p28d)")
        }
    }
}
