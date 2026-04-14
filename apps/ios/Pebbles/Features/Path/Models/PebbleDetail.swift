import Foundation

// MARK: - Ref types (detail-view-local; intentionally not reusing the picker models)

struct EmotionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
    let color: String
}

struct DomainRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

struct SoulRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

struct CollectionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

// MARK: - PebbleDetail

/// Read model for the detail sheet. Decodes a single pebble row with embedded
/// relations via PostgREST. Junction-table wrappers are flattened during
/// decoding so the view sees clean `domains`/`souls`/`collections` arrays.
struct PebbleDetail: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: Visibility
    let emotion: EmotionRef
    let domains: [DomainRef]
    let souls: [SoulRef]
    let collections: [CollectionRef]

    /// Derived from `intensity` + `positiveness`. DB remains source of truth.
    var valence: Valence {
        switch (positiveness, intensity) {
        case (-1, 1): return .lowlightSmall
        case (-1, 2): return .lowlightMedium
        case (-1, 3): return .lowlightLarge
        case (0, 1):  return .neutralSmall
        case (0, 2):  return .neutralMedium
        case (0, 3):  return .neutralLarge
        case (1, 1):  return .highlightSmall
        case (1, 2):  return .highlightMedium
        case (1, 3):  return .highlightLarge
        default:      return .neutralMedium // DB has CHECK constraints; this is defensive only
        }
    }

    // MARK: Decoding

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotion
        case pebbleDomains = "pebble_domains"
        case pebbleSouls = "pebble_souls"
        case collectionPebbles = "collection_pebbles"
    }

    private struct DomainWrapper: Decodable { let domain: DomainRef }
    private struct SoulWrapper: Decodable { let soul: SoulRef }
    private struct CollectionWrapper: Decodable { let collection: CollectionRef }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(UUID.self, forKey: .id)
        self.name = try c.decode(String.self, forKey: .name)
        self.description = try c.decodeIfPresent(String.self, forKey: .description)
        self.happenedAt = try c.decode(Date.self, forKey: .happenedAt)
        self.intensity = try c.decode(Int.self, forKey: .intensity)
        self.positiveness = try c.decode(Int.self, forKey: .positiveness)
        self.visibility = try c.decode(Visibility.self, forKey: .visibility)
        self.emotion = try c.decode(EmotionRef.self, forKey: .emotion)

        let domainWrappers = try c.decodeIfPresent([DomainWrapper].self, forKey: .pebbleDomains) ?? []
        self.domains = domainWrappers.map(\.domain)

        let soulWrappers = try c.decodeIfPresent([SoulWrapper].self, forKey: .pebbleSouls) ?? []
        self.souls = soulWrappers.map(\.soul)

        let collectionWrappers = try c.decodeIfPresent([CollectionWrapper].self, forKey: .collectionPebbles) ?? []
        self.collections = collectionWrappers.map(\.collection)
    }
}
