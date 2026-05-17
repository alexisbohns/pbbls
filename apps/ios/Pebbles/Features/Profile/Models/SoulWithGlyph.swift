import Foundation

/// A soul together with its joined glyph and the live count of pebbles
/// linked to it. Decoded from a single PostgREST request:
///
///     supabase.from("souls")
///         .select("""
///             id, name, glyph_id,
///             glyphs(id, name, strokes, view_box),
///             pebbles_count:pebble_souls(count)
///         """)
///
/// PostgREST nests the joined glyph row under the relation name (`glyphs`)
/// and returns the aggregate as a single-element array of `{count: Int}`
/// objects (e.g. `[{ "count": 12 }]`). A soul with zero linked pebbles is
/// expected to come back as `[{ "count": 0 }]`; an empty array is treated
/// as 0 defensively.
struct SoulWithGlyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID
    let glyph: Glyph
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId      = "glyph_id"
        case glyph        = "glyphs"
        case pebblesCount = "pebbles_count"
    }

    private struct CountWrapper: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id      = try c.decode(UUID.self,   forKey: .id)
        name    = try c.decode(String.self, forKey: .name)
        glyphId = try c.decode(UUID.self,   forKey: .glyphId)
        glyph   = try c.decode(Glyph.self,  forKey: .glyph)
        let wraps = try c.decodeIfPresent([CountWrapper].self, forKey: .pebblesCount) ?? []
        pebblesCount = wraps.first?.count ?? 0
    }

    // Required: providing any initializer in the struct body suppresses
    // the synthesized memberwise init. Preview and test code construct
    // instances without a network response, so re-declare it explicitly.
    init(id: UUID, name: String, glyphId: UUID, glyph: Glyph, pebblesCount: Int) {
        self.id = id
        self.name = name
        self.glyphId = glyphId
        self.glyph = glyph
        self.pebblesCount = pebblesCount
    }

    /// Convenience for code paths that already hold a `Soul` and need to
    /// drop the joined glyph (e.g. passing into `EditSoulSheet` which only
    /// needs the `Soul` shape).
    var soul: Soul {
        Soul(id: id, name: name, glyphId: glyphId)
    }
}
