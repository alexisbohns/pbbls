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
        domains: [DomainRef] = [DomainRef(id: UUID(), name: "Work")],
        souls: [Soul] = [],
        collections: [PebbleCollection] = []
    ) throws -> PebbleDetail {
        // Build JSON and decode — mirrors how PebbleDetail is actually constructed.
        // PebbleDetail has a custom init(from: Decoder), so we can't memberwise-construct it.
        let emotionJSON: [String: Any] = [
            "id": emotionId.uuidString,
            "name": "Joy",
            "color": "#FFD166"
        ]
        let domainsJSON = domains.map { d in ["domain": ["id": d.id.uuidString, "name": d.name]] }
        let soulsJSON = souls.map { s in ["soul": ["id": s.id.uuidString, "name": s.name]] }
        let collectionsJSON = collections.map { c in ["collection": ["id": c.id.uuidString, "name": c.name]] }

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
            let c = try dec.singleValueContainer()
            let s = try c.decode(String.self)
            guard let d = formatter.date(from: s) else {
                throw DecodingError.dataCorruptedError(in: c, debugDescription: "bad date")
            }
            return d
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
            domains: [DomainRef(id: domainId, name: "Work")],
            souls: [Soul(id: soulId, name: "Me")],
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
}
