import Foundation
import Testing
@testable import Pebbles

@Suite("SoulInsertPayload encoding")
struct SoulInsertPayloadEncodingTests {

    private let userId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    private func encode(_ payload: SoulInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes user_id, name, glyph_id with snake_case keys")
    func snakeCaseKeys() throws {
        let payload = SoulInsertPayload(userId: userId, name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["user_id"] as? String == userId.uuidString)
        #expect(json["name"] as? String == "Alex")
        #expect(json["glyph_id"] as? String == glyphId.uuidString)
    }

    @Test("does not emit a top-level id field")
    func noIdField() throws {
        let payload = SoulInsertPayload(userId: userId, name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["id"] == nil)
    }
}
