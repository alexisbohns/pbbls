package app.pbbls.android.features.glyph.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.BuyGlyphResult
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.models.MarketGlyphRow
import app.pbbls.android.features.glyph.models.MineGlyphRow
import app.pbbls.android.features.glyph.models.OwnedGlyphRow
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Market reads + the `buy_glyph` purchase — ports iOS `GlyphMarketService`
 * (M43 design D4/D9). Three PostgREST reads (no RPCs) and one RPC whose
 * scalar jsonb result decodes directly (never a single-row accessor).
 * Methods throw; callers own view state and map errors through
 * [glyphMarketErrorMessage].
 */
class GlyphMarketService(
    private val supabase: SupabaseService,
) {
    /**
     * The Mine tab: the caller's creations (newest first, price from the
     * embedded approved+listed submission) THEN system glyphs — Android keeps
     * system glyphs pickable (design D7, a named deviation from iOS's
     * `eq(user_id, me)` which silently drops them).
     */
    suspend fun listMine(): List<GlyphGridItem> {
        val me = requireUserId()
        val rows =
            supabase.client
                .from("glyphs")
                .select(
                    Columns.raw(
                        "id, name, strokes, view_box, user_id, created_at, glyph_submissions(price, status, listed)",
                    ),
                ) {
                    order("created_at", Order.DESCENDING)
                }.decodeList<MineGlyphRow>()
        val (own, rest) = rows.partition { it.userId == me }
        val system = rest.filter { it.userId == null }
        return (own + system).map { row ->
            GlyphGridItem(
                glyph = row.toGlyph(),
                price = row.listedPrice,
                owned = false,
                createdAt = row.createdAt,
                acquiredAt = null,
            )
        }
    }

    /** The Owned tab: entitlements (RLS-scoped — no user filter), newest acquisition first. */
    suspend fun listOwned(): List<GlyphGridItem> =
        supabase.client
            .from("glyph_entitlements")
            .select(
                Columns.raw("price_paid, created_at, glyphs(id, name, strokes, view_box, user_id, created_at)"),
            ) {
                order("created_at", Order.DESCENDING)
            }.decodeList<OwnedGlyphRow>()
            .map { row ->
                GlyphGridItem(
                    glyph = row.glyph.toGlyph(),
                    price = row.pricePaid,
                    owned = true,
                    createdAt = row.glyph.createdAt,
                    acquiredAt = row.acquiredAt,
                )
            }

    /**
     * The Commu tab: `v_glyph_market` minus the caller's own creations (the
     * view does NOT exclude them — the `.neq` is load-bearing). The picker
     * additionally client-filters `!owned` (design D10).
     */
    suspend fun listCommunity(): List<GlyphGridItem> {
        val me = requireUserId()
        return supabase.client
            .from("v_glyph_market")
            .select(Columns.raw("id, user_id, name, strokes, view_box, created_at, price, owned")) {
                filter { neq("user_id", me) }
                order("created_at", Order.DESCENDING)
            }.decodeList<MarketGlyphRow>()
            .map { row ->
                GlyphGridItem(
                    glyph = row.toGlyph(),
                    price = row.price,
                    owned = row.owned,
                    createdAt = row.createdAt,
                    acquiredAt = null,
                )
            }
    }

    /**
     * `buy_glyph(p_glyph_id) returns jsonb` — success inserts the entitlement,
     * credits the creator, and returns the buyer's new `{entitlement_id,
     * balance}`. Errors surface as Postgres exception text
     * (`insufficient_karma` bubbles from `spend_karma`); the message must
     * reach the caller intact for [glyphMarketErrorMessage]'s substring match.
     */
    suspend fun buy(glyphId: String): BuyGlyphResult =
        supabase.client.postgrest
            .rpc(
                "buy_glyph",
                buildJsonObject { put("p_glyph_id", glyphId) },
            ).decodeAs()

    private fun requireUserId(): String =
        supabase.session?.user?.id
            ?: throw IllegalStateException("glyph market without session")
}

/**
 * iOS `friendlyMessage` — substring-contains on the lowercased error text, in
 * the iOS order, mapped to the localized catalog (pure; JVM-tested).
 */
fun glyphMarketErrorMessage(message: String?): Int {
    val lowered = message?.lowercase() ?: return R.string.glyph_error_generic
    return when {
        "insufficient_karma" in lowered -> R.string.glyph_error_insufficient_karma
        "not_in_market" in lowered -> R.string.glyph_error_not_in_market
        "already_owned" in lowered -> R.string.glyph_error_already_owned
        "cannot_buy_own" in lowered -> R.string.glyph_error_cannot_buy_own
        else -> R.string.glyph_error_generic
    }
}

/** CompositionLocal for [GlyphMarketService] — see [LocalGlyphService] (D4). */
val LocalGlyphMarketService =
    staticCompositionLocalOf<GlyphMarketService> {
        error("LocalGlyphMarketService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
