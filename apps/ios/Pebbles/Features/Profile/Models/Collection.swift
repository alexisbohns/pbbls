import Foundation

/// Mode variants for a collection. Mirrors the `mode` check constraint on
/// `public.collections`: `('stack', 'pack', 'track')` or `null`.
enum CollectionMode: String, Decodable, CaseIterable, Hashable {
    case stack
    case pack
    case track
}

/// View-model for a collection row. Not the storage shape — `pebbleCount` comes
/// from a PostgREST nested aggregate (`pebble_count:collection_pebbles(count)`)
/// that returns `[{ "count": N }]`. The custom decoder below unwraps that into
/// a plain `Int`, and falls back to `0` when the aggregate is absent (e.g. a
/// single-row fetch for the detail header).
struct Collection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let mode: CollectionMode?
    let pebbleCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, mode
        case pebbleCount = "pebble_count"
    }

    private struct CountWrapper: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.name = try container.decode(String.self, forKey: .name)
        self.mode = try container.decodeIfPresent(CollectionMode.self, forKey: .mode)

        if let wrappers = try? container.decode([CountWrapper].self, forKey: .pebbleCount) {
            self.pebbleCount = wrappers.first?.count ?? 0
        } else {
            self.pebbleCount = 0
        }
    }
}
