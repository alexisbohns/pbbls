import Foundation
import Supabase

/// The chooser's "Add <name>" (souls spec S4): one `souls` row with the
/// system default glyph, the payload `CreateSoulSheet` sends. A single-table,
/// single-row insert, so no RPC (AGENTS.md).
enum SoulInlineCreate {
    static let select = "id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"

    static func create(name: String, userId: UUID, client: SupabaseClient) async throws -> SoulWithGlyph {
        let payload = SoulInsertPayload(
            userId: userId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            glyphId: SystemGlyph.default
        )
        return try await client
            .from("souls")
            .insert(payload)
            .select(select)
            .single()
            .execute()
            .value
    }
}
