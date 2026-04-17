import Foundation
import Testing
@testable import Pebbles

@Suite("ComposePebbleResponse decoding")
struct ComposePebbleResponseDecodingTests {

    @Test("decodes a successful compose response")
    func decodesSuccess() throws {
        let json = Data("""
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "render_svg": "<svg xmlns=\\"http://www.w3.org/2000/svg\\"></svg>",
          "render_manifest": [{"type":"glyph","delay":0,"duration":800}],
          "render_version": "0.1.0"
        }
        """.utf8)

        let decoded = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)

        #expect(decoded.pebbleId.uuidString.lowercased() == "550e8400-e29b-41d4-a716-446655440000")
        #expect(decoded.renderSvg?.hasPrefix("<svg") == true)
        #expect(decoded.renderVersion == "0.1.0")
    }

    @Test("decodes a soft-success 5xx response (render fields null)")
    func decodesSoftFailure() throws {
        let json = Data("""
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "error": "compose failed: engine exploded"
        }
        """.utf8)

        let decoded = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)

        #expect(decoded.pebbleId.uuidString.lowercased() == "550e8400-e29b-41d4-a716-446655440000")
        #expect(decoded.renderSvg == nil)
        #expect(decoded.renderVersion == nil)
    }
}
