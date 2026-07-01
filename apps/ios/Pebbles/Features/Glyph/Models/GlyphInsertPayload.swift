import Foundation

/// Body for a direct `INSERT INTO public.glyphs` via the Supabase Swift SDK.
/// Single-table write — no RPC needed (see `AGENTS.md`).
///
/// The glyphs table has no shape column — glyphs are shape-agnostic squares
/// (issue #278, #503). The payload carries only user_id, strokes, view_box, name.
struct GlyphInsertPayload: Encodable {
    let userId: UUID
    let strokes: [GlyphStroke]
    let viewBox: String
    let name: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case viewBox = "view_box"
        case strokes
        case name
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(userId, forKey: .userId)
        try container.encode(strokes, forKey: .strokes)
        try container.encode(viewBox, forKey: .viewBox)
        // Explicit null so absence is unambiguous.
        try container.encode(name, forKey: .name)
    }
}
