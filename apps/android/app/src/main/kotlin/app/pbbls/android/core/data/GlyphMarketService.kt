package app.pbbls.android.core.data

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.BuyGlyphResult
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.model.MarketGlyphRow
import app.pbbls.android.core.model.MineGlyphRow
import app.pbbls.android.core.model.OwnedGlyphRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The glyph-market seam (#849) — what the store's ViewModel is tested against.
 *
 * [buy] is the one call in this app that spends the user's karma, so its test
 * is the reason this interface exists.
 */
interface GlyphMarketServicing {
    suspend fun listMine(): List<GlyphGridItem>

    suspend fun listOwned(): List<GlyphGridItem>

    suspend fun listCommunity(): List<GlyphGridItem>

    suspend fun buy(glyphId: String): BuyGlyphResult

    /**
     * Every purchase that landed, as it lands (#940). The store's list sits
     * beside the detail on large screens and never pauses, so it cannot rely
     * on a resume refresh to learn about one.
     *
     * Relies on the implementation being a `@Singleton`: every host's buy and
     * the store's collector must share one instance.
     */
    val purchases: SharedFlow<GlyphPurchased>
}

/** A `buy_glyph` that succeeded: which glyph, and the server's answer. */
data class GlyphPurchased(
    val glyphId: String,
    val result: BuyGlyphResult,
)

/**
 * Market reads + the `buy_glyph` purchase — ports iOS `GlyphMarketService`
 * (M43 design D4/D9). Three PostgREST reads (no RPCs) and one RPC whose
 * scalar jsonb result decodes directly (never a single-row accessor).
 * Methods throw; callers own view state and map errors through
 * [glyphMarketErrorMessage].
 */

@Singleton
class GlyphMarketService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : GlyphMarketServicing {
        // One flow for the app because this service is a @Singleton: a second
        // instance would announce to nobody. `emit` suspends only while a
        // collector's buffer is full, and `buy` runs uncancellable, so no
        // purchase is dropped; with no collector (the store is not open) there
        // is nothing to tell.
        private val _purchases = MutableSharedFlow<GlyphPurchased>(extraBufferCapacity = 8)
        override val purchases: SharedFlow<GlyphPurchased> = _purchases.asSharedFlow()

        /**
         * The Mine tab: the caller's creations (newest first, price from the
         * embedded approved+listed submission) THEN system glyphs — Android keeps
         * system glyphs pickable (design D7, a named deviation from iOS's
         * `eq(user_id, me)` which silently drops them) — membership is
         * `is_system`, not a null owner (#872).
         */
        override suspend fun listMine(): List<GlyphGridItem> {
            val me = requireUserId()
            val rows =
                supabase.client
                    .from("glyphs")
                    .select(
                        Columns.raw(
                            "id, name, strokes, view_box, user_id, is_system, created_at, " +
                                "glyph_submissions(price, status, listed)",
                        ),
                    ) {
                        order("created_at", Order.DESCENDING)
                    }.decodeList<MineGlyphRow>()
            return mineTab(rows, me).map { row ->
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
        override suspend fun listOwned(): List<GlyphGridItem> =
            supabase.client
                .from("glyph_entitlements")
                .select(
                    Columns.raw(
                        "price_paid, created_at, glyphs(id, name, strokes, view_box, user_id, is_system, created_at)",
                    ),
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
        override suspend fun listCommunity(): List<GlyphGridItem> {
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
         *
         * A success is announced on [purchases] before it returns, so it runs
         * inside the caller's uncancellable section (`GlyphPurchase.buyAndRecord`).
         */
        override suspend fun buy(glyphId: String): BuyGlyphResult {
            val result =
                supabase.client.postgrest
                    .rpc(
                        "buy_glyph",
                        buildJsonObject { put("p_glyph_id", glyphId) },
                    ).decodeAs<BuyGlyphResult>()
            _purchases.emit(GlyphPurchased(glyphId, result))
            return result
        }

        private fun requireUserId(): String =
            supabase.session?.user?.id
                ?: throw IllegalStateException("glyph market without session")

        companion object {
            /**
             * The Mine tab's membership and order: the caller's own creations
             * (server order, newest first) then system glyphs — design D7, the
             * named deviation from iOS's `eq(user_id, me)`.
             *
             * Keyed on [MineGlyphRow.isSystem], never on `userId == null` (#872):
             * `purge_account` anonymizes a SOLD glyph when its creator deletes
             * their account, and that row must not appear in a stranger's Mine tab
             * as a free first-party seed.
             */
            fun mineTab(
                rows: List<MineGlyphRow>,
                me: String,
            ): List<MineGlyphRow> {
                val (own, rest) = rows.partition { it.userId == me }
                return own + rest.filter { it.isSystem }
            }
        }
    }

/**
 * iOS `friendlyMessage` — substring-contains on the lowercased error text, in
 * the iOS order, mapped to the localized catalog (pure; JVM-tested).
 */
@StringRes
fun glyphMarketErrorMessage(error: DataError): Int =
    when (error) {
        DataError.Network -> R.string.error_offline
        is DataError.Conflict ->
            when (error.code) {
                "insufficient_karma" -> R.string.glyph_error_insufficient_karma
                "not_in_market" -> R.string.glyph_error_not_in_market
                "already_owned" -> R.string.glyph_error_already_owned
                "cannot_buy_own" -> R.string.glyph_error_cannot_buy_own
                else -> R.string.glyph_error_generic
            }

        DataError.Unauthorized,
        DataError.NotFound,
        DataError.Quota,
        is DataError.Unknown,
        -> R.string.glyph_error_generic
    }
