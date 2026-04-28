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
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))

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
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))

        let ids = json["domain_ids"] as? [String] ?? []
        #expect(ids.count == 1)
        #expect(ids.first == draft.domainId?.uuidString)
    }

    @Test("soul_ids is empty array when soulId is nil")
    func emptySoulIds() throws {
        let draft = makeValidDraft(soulId: nil)
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))

        let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
        #expect(ids.isEmpty)
    }

    @Test("soul_ids is single-element array when soulId is set")
    func singleSoulId() throws {
        let soulId = UUID()
        let draft = makeValidDraft(soulId: soulId)
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))

        let ids = json["soul_ids"] as? [String] ?? []
        #expect(ids == [soulId.uuidString])
    }

    @Test("collection_ids follows the same pattern as soul_ids")
    func collectionIds() throws {
        let collectionId = UUID()
        let draftWith = makeValidDraft(collectionId: collectionId)
        let jsonWith = try encode(PebbleUpdatePayload(from: draftWith, userId: UUID()))
        #expect((jsonWith["collection_ids"] as? [String]) == [collectionId.uuidString])

        let draftWithout = makeValidDraft(collectionId: nil)
        let jsonWithout = try encode(PebbleUpdatePayload(from: draftWithout, userId: UUID()))
        #expect((jsonWithout["collection_ids"] as? [String])?.isEmpty == true)
    }

    @Test("description encodes as null when empty-string-trimmed")
    func emptyDescriptionBecomesNull() throws {
        var draft = makeValidDraft()
        draft.description = "   "
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))
        #expect(json["description"] is NSNull)
    }

    @Test("encodes glyph_id as null when draft has no glyph")
    func nullGlyphId() throws {
        let draft = makeValidDraft()
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))
        #expect(json["glyph_id"] is NSNull)
    }

    @Test("encodes glyph_id as uuid string when set")
    func setGlyphId() throws {
        let glyphId = UUID()
        var draft = makeValidDraft()
        draft.glyphId = glyphId
        let json = try encode(PebbleUpdatePayload(from: draft, userId: UUID()))
        #expect((json["glyph_id"] as? String) == glyphId.uuidString)
    }

    /// Regression guard for the bug discovered during #278 manual testing:
    /// the Supabase SDK's `.functions.invoke(body:)` path uses a plain
    /// JSONEncoder whose default date strategy emits Double seconds since
    /// reference date (e.g. 798043440) — which Postgres' timestamptz cast
    /// rejects with "date/time field value out of range". The payload must
    /// encode `happened_at` as an ISO8601 string regardless of encoder
    /// configuration. This test mimics the SDK's default-encoder behavior.
    @Test("happened_at is always an ISO8601 string even with a default encoder")
    func iso8601DateWithDefaultEncoder() throws {
        let fixedDate = Date(timeIntervalSince1970: 1_712_345_678)
        var draft = makeValidDraft()
        draft.happenedAt = fixedDate

        let data = try JSONEncoder().encode(PebbleUpdatePayload(from: draft, userId: UUID()))
        let object = try JSONSerialization.jsonObject(with: data)
        let json = try #require(object as? [String: Any])

        let happenedAt = try #require(json["happened_at"] as? String)
        #expect(happenedAt == "2024-04-05T19:34:38Z")
    }
}
