import Foundation
import Testing
@testable import Pebbles

@Suite("CompletePebblePayload encoding")
struct CompletePebblePayloadEncodingTests {

    private func object(_ payload: CompletePebblePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private let momentId = UUID(uuidString: "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B")!

    @Test("moment_id alone: every other key omitted")
    func minimal() throws {
        let json = try object(CompletePebblePayload(momentId: momentId))
        #expect(json.keys.sorted() == ["moment_id"])
        #expect(json["moment_id"] as? String == "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b")
    }

    @Test("a full completion, lowercase ids, visibility raw value")
    func full() throws {
        let emotion = UUID(uuidString: "22222222-2222-4222-8222-222222222222")!
        let domain = UUID(uuidString: "33333333-3333-4333-8333-333333333333")!
        var payload = CompletePebblePayload(momentId: momentId)
        payload.emotionId = emotion
        payload.intensity = 2
        payload.positiveness = 1
        payload.visibility = .private
        payload.domainIds = [domain]
        payload.soulIds = []
        payload.collectionIds = []
        let json = try object(payload)
        #expect(json["emotion_id"] as? String == "22222222-2222-4222-8222-222222222222")
        #expect(json["intensity"] as? Int == 2)
        #expect(json["positiveness"] as? Int == 1)
        #expect(json["visibility"] as? String == "private")
        #expect(json["domain_ids"] as? [String] == ["33333333-3333-4333-8333-333333333333"])
        #expect((json["soul_ids"] as? [String])?.isEmpty == true)
        #expect((json["collection_ids"] as? [String])?.isEmpty == true)
        #expect(json["new_souls"] == nil)
        #expect(json["cards"] == nil)
    }
}
