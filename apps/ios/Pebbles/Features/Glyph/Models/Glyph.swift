import Foundation

/// Full glyph model. Replaces the minimal read-only stub that used to live in
/// `Features/Profile/Models/Glyph.swift`.
///
/// Stored in `public.glyphs`. `viewBox` is always `"0 0 200 200"` for glyphs
/// carved on iOS; imported web glyphs may use different viewBox values.
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case strokes
        case viewBox = "view_box"
    }
}
