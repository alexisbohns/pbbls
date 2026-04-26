import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleCreatePayload encoding")
struct PebbleCreatePayloadEncodingTests {

    private func encode(_ payload: PebbleCreatePayload) throws -> [String: Any] {
        let encoder = JSONEncoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        encoder.dateEncodingStrategy = .custom { date, enc in
            var container = enc.singleValueContainer()
            try container.encode(formatter.string(from: date))
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
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

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
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

        let ids = json["domain_ids"] as? [String] ?? []
        #expect(ids.count == 1)
        #expect(ids.first == draft.domainId?.uuidString)
    }

    @Test("soul_ids is empty array when soulId is nil")
    func emptySoulIds() throws {
        let draft = makeValidDraft(soulId: nil)
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

        let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
        #expect(ids.isEmpty)
    }

    @Test("soul_ids is single-element array when soulId is set")
    func singleSoulId() throws {
        let soulId = UUID()
        let draft = makeValidDraft(soulId: soulId)
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

        let ids = json["soul_ids"] as? [String] ?? []
        #expect(ids == [soulId.uuidString])
    }

    @Test("collection_ids follows the same pattern as soul_ids")
    func collectionIds() throws {
        let collectionId = UUID()
        let draftWith = makeValidDraft(collectionId: collectionId)
        let jsonWith = try encode(PebbleCreatePayload(from: draftWith, userId: UUID()))
        #expect((jsonWith["collection_ids"] as? [String]) == [collectionId.uuidString])

        let draftWithout = makeValidDraft(collectionId: nil)
        let jsonWithout = try encode(PebbleCreatePayload(from: draftWithout, userId: UUID()))
        #expect((jsonWithout["collection_ids"] as? [String])?.isEmpty == true)
    }

    @Test("description encodes as null when empty-string-trimmed")
    func emptyDescriptionBecomesNull() throws {
        var draft = makeValidDraft()
        draft.description = "   "
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))
        #expect(json["description"] is NSNull)
    }

    @Test("encodes glyph_id as null when draft has no glyph")
    func nullGlyphId() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))
        #expect(json["glyph_id"] is NSNull)
    }

    @Test("encodes glyph_id as uuid string when set")
    func setGlyphId() throws {
        let glyphId = UUID()
        var draft = makeValidDraft()
        draft.glyphId = glyphId
        let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))
        #expect((json["glyph_id"] as? String) == glyphId.uuidString)
    }

    @Test("emits snaps array when attachedSnap present")
    func encodesAttachedSnap() throws {
        let snapId = UUID()
        let userId = UUID()
        var draft = makeValidDraft()
        draft.attachedSnap = AttachedSnap(
            id: snapId,
            localThumb: Data(),
            state: .uploaded
        )

        let payload = PebbleCreatePayload(from: draft, userId: userId)
        let json = try encode(payload)

        let snaps = try #require(json["snaps"] as? [[String: Any]])
        try #require(snaps.count == 1)
        #expect((snaps[0]["id"] as? String)?.lowercased() == snapId.uuidString.lowercased())
        #expect(snaps[0]["storage_path"] as? String == "\(userId.uuidString.lowercased())/\(snapId.uuidString.lowercased())")
        #expect(snaps[0]["sort_order"] as? Int == 0)
    }

    @Test("omits snaps key when attachedSnap is nil")
    func omitsSnapsWhenAbsent() throws {
        let draft = makeValidDraft()
        let payload = PebbleCreatePayload(from: draft, userId: UUID())
        let json = try encode(payload)
        #expect(json["snaps"] == nil)
    }
}
