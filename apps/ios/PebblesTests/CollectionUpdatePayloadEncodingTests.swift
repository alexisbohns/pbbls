import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionUpdatePayload encoding")
struct CollectionUpdatePayloadEncodingTests {

    private func encode(_ payload: CollectionUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes name and mode rawValue")
    func basicEncoding() throws {
        let payload = CollectionUpdatePayload(name: "Summer", mode: "pack", glyphId: nil)
        let json = try encode(payload)
        #expect(json["name"] as? String == "Summer")
        #expect(json["mode"] as? String == "pack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionUpdatePayload(name: "Modeless", mode: nil, glyphId: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let object = try JSONSerialization.jsonObject(with: data)
        let json = try #require(object as? [String: Any])
        #expect(json["mode"] is NSNull)
    }

    @Test("each mode rawValue round-trips")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let payload = CollectionUpdatePayload(name: "x", mode: raw, glyphId: nil)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }

    @Test("glyph_id is always sent: a lowercase id, or an explicit null that clears it (R11)")
    func glyphId() throws {
        let id = UUID(uuidString: "66666666-6666-4666-8666-666666666666")!
        let set = try encode(CollectionUpdatePayload(name: "A", mode: nil, glyphId: id))
        #expect(set["glyph_id"] as? String == "66666666-6666-4666-8666-666666666666")
        let cleared = try encode(CollectionUpdatePayload(name: "A", mode: nil, glyphId: nil))
        #expect(cleared.keys.contains("glyph_id"))
        #expect(cleared["glyph_id"] is NSNull)
    }
}
