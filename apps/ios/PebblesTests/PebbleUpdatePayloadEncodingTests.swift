import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleUpdatePayload encoding")
struct PebbleUpdatePayloadEncodingTests {

    private func encode(_ payload: PebbleUpdatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var c = enc.singleValueContainer()
            try c.encode(formatter.string(from: date))
        }
        let data = try encoder.encode(payload)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    private func makeValidDraft(
        soulId: UUID? = nil,
        collectionId: UUID? = nil
    ) -> PebbleDraft {
        var draft = PebbleDraft()
        draft.name = "Test"
        draft.description = "body"
        draft.emotionId = UUID()
        draft.domainId = UUID()
        draft.valence = .highlightLarge
        draft.soulId = soulId
        draft.collectionId = collectionId
        draft.visibility = .private
        return draft
    }

    @Test("encodes all scalar fields with snake_case keys")
    func scalarKeys() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleUpdatePayload(from: draft))

        #expect(json["name"] as? String == "Test")
        #expect(json["description"] as? String == "body")
        #expect(json["happened_at"] is String)
        #expect(json["emotion_id"] is String)
        #expect(json["intensity"] as? Int == 3)
        #expect(json["positiveness"] as? Int == 1)
        #expect(json["visibility"] as? String == "private")
    }

    @Test("domain_ids is always a single-element array")
    func domainIds() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["domain_ids"] as? [String] ?? []
        #expect(ids.count == 1)
        #expect(ids.first == draft.domainId?.uuidString)
    }

    @Test("soul_ids is empty array when soulId is nil")
    func emptySoulIds() throws {
        let draft = makeValidDraft(soulId: nil)
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
        #expect(ids.isEmpty)
    }

    @Test("soul_ids is single-element array when soulId is set")
    func singleSoulId() throws {
        let soulId = UUID()
        let draft = makeValidDraft(soulId: soulId)
        let json = try encode(PebbleUpdatePayload(from: draft))

        let ids = json["soul_ids"] as? [String] ?? []
        #expect(ids == [soulId.uuidString])
    }

    @Test("collection_ids follows the same pattern as soul_ids")
    func collectionIds() throws {
        let collectionId = UUID()
        let draftWith = makeValidDraft(collectionId: collectionId)
        let jsonWith = try encode(PebbleUpdatePayload(from: draftWith))
        #expect((jsonWith["collection_ids"] as? [String]) == [collectionId.uuidString])

        let draftWithout = makeValidDraft(collectionId: nil)
        let jsonWithout = try encode(PebbleUpdatePayload(from: draftWithout))
        #expect((jsonWithout["collection_ids"] as? [String])?.isEmpty == true)
    }

    @Test("description encodes as null when empty-string-trimmed")
    func emptyDescriptionBecomesNull() throws {
        var draft = makeValidDraft()
        draft.description = "   "
        let json = try encode(PebbleUpdatePayload(from: draft))
        #expect(json["description"] is NSNull)
    }
}
