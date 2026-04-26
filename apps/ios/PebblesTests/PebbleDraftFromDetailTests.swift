import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDraft init(from: PebbleDetail)")
struct PebbleDraftFromDetailTests {

    private func makeDetail(
        name: String = "Shipped",
        description: String? = "Finally.",
        positiveness: Int = 1,
        intensity: Int = 3,
        visibility: Visibility = .private,
        emotionId: UUID = UUID(),
        domains: [DomainRef] = [DomainRef(id: UUID(), slug: "zoe", name: "Work")],
        souls: [Soul] = [],
        collections: [PebbleCollection] = []
    ) throws -> PebbleDetail {
        // Build JSON and decode — mirrors how PebbleDetail is actually constructed.
        // PebbleDetail has a custom init(from: Decoder), so we can't memberwise-construct it.
        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "slug": "joy",
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = domains.map { domain in ["domain": ["id": domain.id.uuidString, "slug": domain.slug, "name": domain.name]] }
        let soulsJSON = souls.map { soul in ["soul": ["id": soul.id.uuidString, "name": soul.name, "glyph_id": soul.glyphId.uuidString]] }
        let collectionsJSON = collections.map { coll in ["collection": ["id": coll.id.uuidString, "name": coll.name]] }

        var root: [String: Any] = [
            "id": UUID().uuidString,
            "name": name,
            "happened_at": "2026-04-14T15:42:00Z",
            "intensity": intensity,
            "positiveness": positiveness,
            "visibility": visibility.rawValue,
            "emotion": emotionJSON,
            "pebble_domains": domainsJSON,
            "pebble_souls": soulsJSON,
            "collection_pebbles": collectionsJSON
        ]
        if let description { root["description"] = description }

        let data = try JSONSerialization.data(withJSONObject: root)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            guard let date = formatter.date(from: iso) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "bad date")
            }
            return date
        }
        return try decoder.decode(PebbleDetail.self, from: data)
    }

    @Test("populates all fields from a fully-populated detail")
    func fullyPopulated() throws {
        let emotionId = UUID()
        let domainId = UUID()
        let soulId = UUID()
        let collectionId = UUID()

        let detail = try makeDetail(
            name: "Shipped",
            description: "Finally.",
            positiveness: 1,
            intensity: 3,
            visibility: .public,
            emotionId: emotionId,
            domains: [DomainRef(id: domainId, slug: "zoe", name: "Work")],
            souls: [Soul(id: soulId, name: "Me", glyphId: UUID())],
            collections: [PebbleCollection(id: collectionId, name: "Wins")]
        )

        let draft = PebbleDraft(from: detail)

        #expect(draft.name == "Shipped")
        #expect(draft.description == "Finally.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == emotionId)
        #expect(draft.domainId == domainId)
        #expect(draft.soulId == soulId)
        #expect(draft.collectionId == collectionId)
        #expect(draft.valence == .highlightLarge)
        #expect(draft.visibility == .public)
    }

    @Test("maps nil description to empty string")
    func nilDescription() throws {
        let detail = try makeDetail(description: nil)
        let draft = PebbleDraft(from: detail)
        #expect(draft.description == "")
    }

    @Test("leaves soulId nil when no souls")
    func noSouls() throws {
        let detail = try makeDetail(souls: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.soulId == nil)
    }

    @Test("leaves collectionId nil when no collections")
    func noCollections() throws {
        let detail = try makeDetail(collections: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.collectionId == nil)
    }

    @Test("leaves domainId nil and draft invalid when domains is empty")
    func emptyDomains() throws {
        let detail = try makeDetail(domains: [])
        let draft = PebbleDraft(from: detail)
        #expect(draft.domainId == nil)
        #expect(draft.isValid == false)
    }

    @Test("derives valence from positiveness and intensity pair")
    func valenceMapping() throws {
        let detail = try makeDetail(positiveness: -1, intensity: 2)
        let draft = PebbleDraft(from: detail)
        #expect(draft.valence == .lowlightMedium)
    }

    @Test("draft.glyphId is populated from detail.glyphId")
    func glyphIdRoundTrip() throws {
        let glyphId = UUID()
        let emotionId = UUID()
        let domainId = UUID()

        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "slug": "joy",
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = [["domain": ["id": domainId.uuidString, "slug": "zoe", "name": "Work"]]]

        var root: [String: Any] = [
            "id": UUID().uuidString,
            "name": "Test Pebble",
            "happened_at": "2026-04-18T12:00:00Z",
            "intensity": 2,
            "positiveness": 1,
            "visibility": "private",
            "glyph_id": glyphId.uuidString,
            "emotion": emotionJSON,
            "pebble_domains": domainsJSON,
            "pebble_souls": [],
            "collection_pebbles": []
        ]

        let data = try JSONSerialization.data(withJSONObject: root)
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        decoder.dateDecodingStrategy = .custom { dec in
            let container = try dec.singleValueContainer()
            let iso = try container.decode(String.self)
            guard let date = formatter.date(from: iso) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "bad date")
            }
            return date
        }
        let detail = try decoder.decode(PebbleDetail.self, from: data)
        let draft = PebbleDraft(from: detail)
        #expect(draft.glyphId == glyphId)
    }
}
