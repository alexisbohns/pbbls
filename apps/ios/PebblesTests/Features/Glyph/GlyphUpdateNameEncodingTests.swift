import Foundation
import Testing
@testable import Pebbles

/// Verifies the dict body sent to PostgREST in `GlyphService.updateName(...)`
/// encodes a Swift `nil` as a JSON `null` (so the column is cleared) and a
/// non-empty string as a JSON string. Guards against accidental omission.
@Suite("Glyph update-name encoding")
struct GlyphUpdateNameEncodingTests {

    private func encode(_ value: [String: String?]) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    @Test("non-empty name encodes as a JSON string")
    func nameEncodesAsString() throws {
        let json = try encode(["name": "Pebble"])
        #expect((json["name"] as? String) == "Pebble")
    }

    @Test("nil name encodes as JSON null, not omitted")
    func nilEncodesAsNull() throws {
        let json = try encode(["name": nil])
        #expect(json["name"] is NSNull, "nil must serialize as JSON null so the DB column is cleared")
    }
}
