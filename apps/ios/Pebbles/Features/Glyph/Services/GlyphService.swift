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

    /// Fetches glyphs visible to the current user. Includes system glyphs
    /// (`user_id is null`) so they remain available in pickers and grids; the
    /// `Glyph.userId` field lets call sites distinguish ownership for
    /// permission-gated UI like rename.
    func list() async throws -> [Glyph] {
        let rows: [Glyph] = try await supabase.client
            .from("glyphs")
            .select("id, name, strokes, view_box, user_id")
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
            name: normalizedName(name)
        )
        let created: Glyph = try await supabase.client
            .from("glyphs")
            .insert(payload)
            .select("id, name, strokes, view_box, user_id")
            .single()
            .execute()
            .value
        return created
    }

    /// Updates a glyph's name. Pass `nil`, `""`, or any whitespace-only string
    /// to clear it. Single-table write — no RPC needed (per AGENTS.md). RLS
    /// `glyphs_update` enforces ownership; no eager session guard here because
    /// a missing session is indistinguishable from a permission error at this
    /// layer.
    func updateName(id: UUID, name: String?) async throws -> Glyph {
        let value = normalizedName(name)
        let updated: Glyph = try await supabase.client
            .from("glyphs")
            .update(["name": value])
            .eq("id", value: id)
            .select("id, name, strokes, view_box, user_id")
            .single()
            .execute()
            .value
        return updated
    }

    private func normalizedName(_ raw: String?) -> String? {
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (trimmed?.isEmpty ?? true) ? nil : trimmed
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
