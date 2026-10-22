import Foundation
import Testing
@testable import Pebbles

/// #1099 D8: `complete_pebble` replaces `domain_ids` and `collection_ids` only
/// when the key is present (`private.jsonb_has`,
/// `20261012090100_moments_split_writers.sql`). An Edit pebble field left as
/// prefilled is omitted, so a pre-split pebble keeps its three domains and two
/// collections; a changed field collapses its set to the one pick.
@Suite("CompletePebblePayload keeps untouched sets")
struct CompletePebblePayloadTests {
    static let momentId = "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e"
    static let domains = ["33333333-3333-4333-8333-333333333331", "33333333-3333-4333-8333-333333333332",
                          "33333333-3333-4333-8333-333333333333"]
    static let collections = ["55555555-5555-4555-8555-555555555551", "55555555-5555-4555-8555-555555555552"]

    /// A pre-split pebble: three domains, two collections, as `v_moments_full` sends it.
    static let multiSet = Data("""
    {
      "id": "\(momentId)", "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Shipped the thing", "description": null,
      "happened_at": "2026-09-29T16:00:00+00:00", "hidden_at": null,
      "created_at": "2026-09-29T16:02:44.9+00:00", "updated_at": "2026-10-12T09:00:00+00:00",
      "snaps": [],
      "pebble": {
        "id": "\(momentId)", "intensity": 3, "positiveness": 1, "visibility": "private",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
        "glyph": null, "souls": [],
        "domains": [
          {"id": "\(domains[0])", "slug": "zoe", "name": "Work"},
          {"id": "\(domains[1])", "slug": "amis", "name": "Friends"},
          {"id": "\(domains[2])", "slug": "corps", "name": "Body"}
        ],
        "collections": [
          {"id": "\(collections[0])", "name": "Autumn", "glyph_id": null},
          {"id": "\(collections[1])", "name": "Wins", "glyph_id": null}
        ]
      }
    }
    """.utf8)

    private func editDraft() throws -> CompleteDraft {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: Self.multiSet)
        return CompleteDraft(from: try #require(detail.pebble))
    }

    private func json(_ payload: CompletePebblePayload) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try #require(String(data: encoder.encode(payload), encoding: .utf8))
    }

    private var moment: UUID { MomentFixtures.uuid(Self.momentId) }

    @Test("an edit that touches neither set omits both keys")
    func untouched() throws {
        var draft = try editDraft()
        draft.emotionId = UUID()
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.domainIds == nil && payload.collectionIds == nil)
        let text = try json(payload)
        #expect(!text.contains("domain_ids"))
        #expect(!text.contains("collection_ids"))
        #expect(text.contains("\"emotion_id\""))
    }

    @Test("another domain collapses the set to that one, and leaves the collections alone")
    func changedDomain() throws {
        var draft = try editDraft()
        let other = UUID()
        draft.domainId = other
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.domainIds == [other])
        #expect(payload.collectionIds == nil)
        #expect(try json(payload).contains("\"domain_ids\":[\"\(other.uuidString.lowercased())\"]"))
    }

    @Test("a cleared collection sends an empty set")
    func clearedCollection() throws {
        var draft = try editDraft()
        draft.collectionId = nil
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.collectionIds == [])
        #expect(try json(payload).contains("\"collection_ids\":[]"))
    }

    @Test("another collection replaces the set with that one")
    func changedCollection() throws {
        var draft = try editDraft()
        let other = UUID()
        draft.collectionId = other
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        #expect(payload.collectionIds == [other])
        #expect(payload.domainIds == nil)
    }

    @Test("a first completion, with no prefill, sends both keys")
    func firstCompletion() throws {
        var draft = CompleteDraft()
        draft.valence = .neutralMedium
        draft.emotionId = UUID()
        draft.domainId = UUID()
        let payload = try #require(CompletePebblePayload(momentId: moment, draft: draft))
        let text = try json(payload)
        #expect(text.contains("\"domain_ids\":["))
        #expect(text.contains("\"collection_ids\":[]"))
    }
}
