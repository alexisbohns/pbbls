import Foundation

/// One row of `v_moments_full` (spec §4): the moment, its snaps, and
/// `pebble` = the CALLER's own pebble on it, or nil (uncompleted, or a
/// connection's moment). Read model of the read page and both edit sheets.
struct MomentDetail: Identifiable, Decodable, Hashable {

    struct Snap: Identifiable, Decodable, Hashable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        private enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder = "sort_order"
        }
    }

    /// `v_moments_full.pebble.souls[]` is `{id, name}` only. The read page
    /// takes each soul's glyph from `ReferenceDataService.souls` (A6).
    struct SoulRef: Identifiable, Decodable, Hashable {
        let id: UUID
        let name: String
    }

    struct CollectionRef: Identifiable, Decodable, Hashable {
        let id: UUID
        let name: String
        let glyphId: UUID?

        private enum CodingKeys: String, CodingKey {
            case id, name
            case glyphId = "glyph_id"
        }
    }

    struct Pebble: Identifiable, Decodable, Hashable {
        let id: UUID
        let intensity: Int
        let positiveness: Int
        let visibility: Visibility
        let emotion: EmotionRef
        let glyph: PebbleGlyph?
        let domains: [DomainRef]
        let souls: [SoulRef]
        let collections: [CollectionRef]

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }

        private enum CodingKeys: String, CodingKey {
            case id, intensity, positiveness, visibility, emotion, glyph, domains, souls, collections
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(UUID.self, forKey: .id)
            intensity = try container.decode(Int.self, forKey: .intensity)
            positiveness = try container.decode(Int.self, forKey: .positiveness)
            visibility = try container.decode(Visibility.self, forKey: .visibility)
            emotion = try container.decode(EmotionRef.self, forKey: .emotion)
            glyph = try container.decodeIfPresent(PebbleGlyph.self, forKey: .glyph)
            domains = try container.decodeIfPresent([DomainRef].self, forKey: .domains) ?? []
            souls = try container.decodeIfPresent([SoulRef].self, forKey: .souls) ?? []
            collections = try container.decodeIfPresent([CollectionRef].self, forKey: .collections) ?? []
        }
    }

    let id: UUID
    let userId: UUID
    let name: String?
    let description: String?
    let happenedAt: Date
    let hiddenAt: Date?
    let createdAt: Date
    let updatedAt: Date
    let snaps: [Snap]
    let pebble: Pebble?

    private enum CodingKeys: String, CodingKey {
        case id, name, description, snaps, pebble
        case userId = "user_id"
        case happenedAt = "happened_at"
        case hiddenAt = "hidden_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        userId = try container.decode(UUID.self, forKey: .userId)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        happenedAt = try container.decodeFlexibleDate(forKey: .happenedAt)
        hiddenAt = try container.decodeFlexibleDateIfPresent(forKey: .hiddenAt)
        createdAt = try container.decodeFlexibleDate(forKey: .createdAt)
        updatedAt = try container.decodeFlexibleDate(forKey: .updatedAt)
        snaps = (try container.decodeIfPresent([Snap].self, forKey: .snaps) ?? [])
            .sorted { $0.sortOrder < $1.sortOrder }
        pebble = try container.decodeIfPresent(Pebble.self, forKey: .pebble)
    }
}
