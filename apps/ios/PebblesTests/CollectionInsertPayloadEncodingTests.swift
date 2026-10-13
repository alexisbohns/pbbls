import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionInsertPayload encoding")
struct CollectionInsertPayloadEncodingTests {

    private let userId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!

    private func encode(_ payload: CollectionInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes user_id as snake_case and name verbatim")
    func userIdAndName() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Summer", mode: "pack", glyphId: nil)
        let json = try encode(payload)
        // Swift's default JSONEncoder UUID strategy is `.deferredToUUID`, which
        // emits the same form as `UUID.uuidString`, so the round-trip is exact.
        #expect(json["user_id"] as? String == userId.uuidString)
        #expect(json["name"] as? String == "Summer")
    }

    @Test("encodes mode rawValue when set")
    func modeWhenSet() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "x", mode: "stack", glyphId: nil)
        let json = try encode(payload)
        #expect(json["mode"] as? String == "stack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Modeless", mode: nil, glyphId: nil)
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
            let payload = CollectionInsertPayload(userId: userId, name: "x", mode: raw, glyphId: nil)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }

    @Test("glyph_id is always sent on insert: a lowercase id, or an explicit null (R11)")
    func glyphId() throws {
        let id = UUID(uuidString: "66666666-6666-4666-8666-666666666666")!
        let set = try encode(CollectionInsertPayload(userId: userId, name: "A", mode: nil, glyphId: id))
        #expect(set["glyph_id"] as? String == "66666666-6666-4666-8666-666666666666")
        let none = try encode(CollectionInsertPayload(userId: userId, name: "A", mode: nil, glyphId: nil))
        #expect(none.keys.contains("glyph_id"))
        #expect(none["glyph_id"] is NSNull)
    }
}
