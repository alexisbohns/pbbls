import Foundation
import Testing
@testable import Pebbles

@Suite("GlyphInsertPayload encoding")
struct GlyphInsertPayloadEncodingTests {

    private func encode(_ payload: GlyphInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    @Test("encodes snake_case keys without shape_id")
    func snakeCaseKeys() throws {
        let userId = UUID()
        let payload = GlyphInsertPayload(
            userId: userId,
            strokes: [GlyphStroke(d: "M0,0 L10,10", width: 6)],
            viewBox: "0 0 200 200",
            name: nil
        )
        let json = try encode(payload)
        #expect((json["user_id"] as? String) == userId.uuidString)
        #expect(json["shape_id"] == nil, "shape_id must be omitted so the DB stores NULL")
        #expect((json["view_box"] as? String) == "0 0 200 200")
        #expect(json["name"] is NSNull)
    }

    @Test("strokes encode as a JSON array of d + width")
    func strokeShape() throws {
        let payload = GlyphInsertPayload(
            userId: UUID(),
            strokes: [
                GlyphStroke(d: "M0,0 L1,1", width: 6),
                GlyphStroke(d: "M2,2 L3,3", width: 6)
            ],
            viewBox: "0 0 200 200",
            name: nil
        )
        let json = try encode(payload)
        let strokes = try #require(json["strokes"] as? [[String: Any]])
        #expect(strokes.count == 2)
        #expect(strokes[0]["d"] as? String == "M0,0 L1,1")
        #expect(strokes[0]["width"] as? Double == 6)
    }
}
