import Foundation

/// A soul together with its joined glyph, decoded from a single PostgREST
/// request:
///
///     supabase.from("souls")
///         .select("id, name, glyph_id, glyphs(id, strokes, view_box)")
///
/// PostgREST nests the joined row under the relation name (`glyphs`).
struct SoulWithGlyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID
    let glyph: Glyph

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId = "glyph_id"
        case glyph = "glyphs"
    }

    /// Convenience for code paths that already hold a `Soul` and need to
    /// drop the joined glyph (e.g. passing into `EditSoulSheet` which only
    /// needs the `Soul` shape).
    var soul: Soul {
        Soul(id: id, name: name, glyphId: glyphId)
    }
}
