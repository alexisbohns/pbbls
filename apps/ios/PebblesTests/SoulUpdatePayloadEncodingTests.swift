import Foundation
import Testing
@testable import Pebbles

@Suite("SoulUpdatePayload encoding")
struct SoulUpdatePayloadEncodingTests {

    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    private func encode(_ payload: SoulUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes name and glyph_id with snake_case keys")
    func snakeCaseKeys() throws {
        let payload = SoulUpdatePayload(name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["name"] as? String == "Alex")
        #expect(json["glyph_id"] as? String == glyphId.uuidString)
    }
}
