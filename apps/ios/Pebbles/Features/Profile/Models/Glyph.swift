import Foundation

/// Minimal read shape for a `glyphs` row. `name` is nullable in the schema,
/// so we model it as optional. Full glyph editing lives in a future feature.
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?
}
