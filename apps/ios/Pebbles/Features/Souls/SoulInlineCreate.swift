import Foundation
import Supabase

/// The chooser's "Add <name>" (souls spec S4): one `souls` row with the
/// system default glyph, the payload `CreateSoulSheet` sends. A single-table,
/// single-row insert, so no RPC (AGENTS.md).
enum SoulInlineCreate {
    static let select = SoulRoute.columns

    /// The host calls `achievements.fireCheck()` after a successful create and
    /// logs failures (the chooser view does).
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
