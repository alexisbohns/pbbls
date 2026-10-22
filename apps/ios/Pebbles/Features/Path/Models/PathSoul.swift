import Foundation

/// One soul on a `path_moments` pebble (#1121): what the Spine needs to name
/// and mark a person without another read. `glyph_id` is absent on a
/// `v_moments_full` soul, so it decodes as optional.
struct PathSoul: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
    let glyphId: UUID?

    private enum CodingKeys: String, CodingKey {
        case id, name
        case glyphId = "glyph_id"
    }

    init(id: UUID, name: String, glyphId: UUID?) {
        self.id = id
        self.name = name
        self.glyphId = glyphId
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        glyphId = try container.decodeIfPresent(UUID.self, forKey: .glyphId)
    }
}
