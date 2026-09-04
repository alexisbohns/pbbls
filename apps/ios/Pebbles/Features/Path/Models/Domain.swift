import Foundation

/// A life domain. Decoded from `v_domains_with_glyph` (D6), which flattens the
/// domain's default glyph onto the row as `strokes` + `view_box`.
///
/// Both glyph fields are optional and defaulted so the type still decodes a
/// plain `domains` table row, and so the memberwise initializer stays source
/// compatible for tests and previews that never care about the glyph.
struct Domain: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    /// The English description. Render `localizedLabel`, never this.
    let label: String
    var strokes: [GlyphStroke]?
    var viewBox: String?

    enum CodingKeys: String, CodingKey {
        case id
        case slug
        case name
        case label
        case strokes
        case viewBox = "view_box"
    }
}
