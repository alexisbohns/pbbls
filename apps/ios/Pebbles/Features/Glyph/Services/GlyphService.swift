import Foundation
import Supabase
import os

/// Thin wrapper over Supabase for the `public.glyphs` table.
///
/// Single-table reads/writes only (see `AGENTS.md` — multi-table ops must
/// become RPCs, but glyphs don't cross table boundaries).
///
/// iOS-carved glyphs are stored with `shape_id = NULL` (made nullable in
/// migration `20260415000001`), matching the issue #278 constraint that the
/// glyph zone is always a square — no per-glyph shape.
@MainActor
struct GlyphService {
    let supabase: SupabaseService

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
