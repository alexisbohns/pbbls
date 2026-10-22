import Foundation

/// A collection as the reference cache holds it. Its glyph is embedded the way
/// the souls query embeds a soul's (`glyphs(id, name, strokes, view_box)`,
/// #1145 D9), optional because `collections.glyph_id` is nullable and a row
/// from an older select has no embed. The held card's band draws it.
struct PebbleCollection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyph: Glyph?

    init(id: UUID, name: String, glyph: Glyph? = nil) {
        self.id = id
        self.name = name
        self.glyph = glyph
    }

    /// Synthesised decoding: an optional `let` reads `decodeIfPresent`, so a
    /// missing `glyphs` key and `null` both decode to no glyph.
    private enum CodingKeys: String, CodingKey {
        case id, name
        case glyph = "glyphs"
    }
}
