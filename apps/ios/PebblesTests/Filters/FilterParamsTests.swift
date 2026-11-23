import Foundation
import Testing
@testable import Pebbles

@Suite("FilterParams")
struct FilterParamsTests {
    @Test("round-trips through JSON")
    func roundTrip() throws {
        var params = FilterParams()
        params.exposure = 0.4
        params.tint = -0.25
        params.grain = 0.2
        params.refocus = 0.5
        params.autoEnhance = true
        let data = try JSONEncoder().encode(params)
        #expect(try JSONDecoder().decode(FilterParams.self, from: data) == params)
    }

    @Test("a missing key decodes as neutral")
    func missingKeys() throws {
        let json = Data(#"{"exposure":0.3}"#.utf8)
        var expected = FilterParams.neutral
        expected.exposure = 0.3
        #expect(try JSONDecoder().decode(FilterParams.self, from: json) == expected)
    }
}
