import Foundation

/// Body for `INSERT INTO public.souls`. RLS requires `user_id` to match
/// `auth.uid()`, so the sheet supplies it from the active session.
struct SoulInsertPayload: Encodable {
    let userId: UUID
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case glyphId = "glyph_id"
    }
}

/// Body for `UPDATE public.souls SET ... WHERE id = ?`. Owned by the
/// caller — `id` is supplied via the `.eq("id", value:)` filter, not in the body.
struct SoulUpdatePayload: Encodable {
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case name
        case glyphId = "glyph_id"
    }
}
