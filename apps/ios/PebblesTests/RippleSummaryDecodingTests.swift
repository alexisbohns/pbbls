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
