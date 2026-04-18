import Foundation
import Supabase
import os

/// Thin wrapper over Supabase for the `public.glyphs` table.
///
/// Single-table reads/writes only (see `AGENTS.md` — multi-table ops must
/// become RPCs, but glyphs don't cross table boundaries).
///
/// The `squareShapeId` is hardcoded from the deterministic id pattern in
/// `packages/supabase/supabase/migrations/20260411000006_deterministic_reference_ids.sql`
/// (`md5('pebble_shapes:' || slug)::uuid`). This satisfies the V1 constraint
/// "Glyph zone is a square, no such thing as shape" without a schema change.
@MainActor
struct GlyphService {
    let supabase: SupabaseService

    /// Deterministic UUID from `md5('pebble_shapes:square')` reinterpreted as UUID.
    static let squareShapeId = UUID(uuidString: "3753e7c7-a7dc-5da8-034c-94968e4c7eba")!

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-service")

    /// Fetches glyphs visible to the current user. In V1 this also includes
    /// system glyphs (user_id is null) — the RLS policy allows them for
    /// domain-default fallback reads elsewhere, and filtering them out
    /// client-side would require adding user_id to the Glyph model.
    /// Not blocking — deferred until the picker needs the distinction.
    func list() async throws -> [Glyph] {
        let rows: [Glyph] = try await supabase.client
            .from("glyphs")
            .select("id, name, strokes, view_box")
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows
    }

    /// Inserts a new glyph owned by the current user. Returns the persisted row.
    func create(strokes: [GlyphStroke], name: String? = nil) async throws -> Glyph {
        guard let userId = supabase.session?.user.id else {
            Self.logger.error("glyph save without session")
            throw GlyphServiceError.missingSession
        }
        let payload = GlyphInsertPayload(
            userId: userId,
            shapeId: Self.squareShapeId,
            strokes: strokes,
            viewBox: "0 0 200 200",
            name: name
        )
        let created: Glyph = try await supabase.client
            .from("glyphs")
            .insert(payload)
            .select("id, name, strokes, view_box")
            .single()
            .execute()
            .value
        return created
    }
}

enum GlyphServiceError: Error, LocalizedError {
    case missingSession

    var errorDescription: String? {
        switch self {
        case .missingSession: return "Please sign in again."
        }
    }
}
