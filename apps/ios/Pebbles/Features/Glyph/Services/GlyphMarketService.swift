import Foundation
import Supabase
import os

/// Reads the three glyph tabs and performs the swap. No RPCs added: Commu uses
/// the existing `v_glyph_market` view, Mine/Owned are single PostgREST reads
/// (one request each, embeds included), and the swap calls `buy_glyph`.
@MainActor
struct GlyphMarketService {
    let supabase: SupabaseService

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-market")

    /// Glyphs I created (any submission state), newest first, with cost badge.
    func listMine() async throws -> [GlyphGridItem] {
        guard let me = supabase.session?.user.id else { throw GlyphMarketError.missingSession }
        let rows: [MineGlyphRow] = try await supabase.client
            .from("glyphs")
            .select("id, name, strokes, view_box, user_id, created_at, glyph_submissions(price, status, listed)")
            .eq("user_id", value: me)
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows.map { $0.toGridItem() }
    }

    /// Glyphs I've swapped, newest acquisition first.
    func listOwned() async throws -> [GlyphGridItem] {
        let rows: [EntitlementRow] = try await supabase.client
            .from("glyph_entitlements")
            .select("price_paid, created_at, glyphs(id, name, strokes, view_box, user_id, created_at)")
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows.map { $0.toGridItem() }
    }

    /// Community marketplace (approved + listed), excluding my own creations.
    func listCommunity() async throws -> [GlyphGridItem] {
        guard let me = supabase.session?.user.id else { throw GlyphMarketError.missingSession }
        let rows: [MarketGlyphRow] = try await supabase.client
            .from("v_glyph_market")
            .select("id, user_id, name, strokes, view_box, created_at, price, owned")
            .neq("user_id", value: me)
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows.map { $0.toGridItem() }
    }

    /// Spends karma for a community glyph. Returns the new balance + entitlement id.
    /// `buy_glyph` returns a scalar `jsonb` object, so decode the body directly —
    /// no `.single()` (that is for `SETOF`/table-returning functions).
    func buy(id: UUID) async throws -> BuyGlyphResult {
        let result: BuyGlyphResult = try await supabase.client
            .rpc("buy_glyph", params: ["p_glyph_id": id.uuidString])
            .execute()
            .value
        return result
    }
}

enum GlyphMarketError: Error, LocalizedError {
    case missingSession

    var errorDescription: String? {
        switch self {
        case .missingSession: return "Please sign in again."
        }
    }
}
