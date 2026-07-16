package app.pbbls.android.features.glyph.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lists glyphs the current user can attach to a pebble (D13) — the
 * `GlyphService.list()` analog. Selection-only in this milestone; no
 * carve/create. `list()` throws to the caller — the glyph picker's
 * `LaunchedEffect` catches and logs (keeps the seam JVM-testable). Mirrors
 * ReferenceDataService.fetchSouls's supabase-kt call shape.
 */
class GlyphService(
    private val supabase: SupabaseService,
) {
    /**
     * Every glyph the user may attach: own + system (`user_id is null`) +
     * marketplace-entitled (#562) — the same set the server-side
     * `can_use_glyph` guard accepts. The `glyphs` RLS SELECT also exposes
     * approved community submissions (browsable, but not owned), so the bare
     * select is still filtered to own+system before the entitled union — a
     * bare list would offer community glyphs whose attachment the server
     * rejects (SQLSTATE 42501).
     */
    suspend fun list(): List<Glyph> =
        coroutineScope {
            val me = supabase.session?.user?.id
            val ownAndSystem =
                async {
                    supabase.client
                        .from("glyphs")
                        .select(Columns.raw("id, name, strokes, view_box, user_id")) {
                            order("created_at", Order.DESCENDING)
                        }.decodeList<Glyph>()
                        .filter { it.userId == null || it.userId == me }
                }
            // glyph_entitlements is RLS-scoped to the caller, so no client
            // filter is needed; entitlement created_at = newest acquisition
            // first (mirrors iOS GlyphMarketService.listOwned).
            val entitled =
                async {
                    supabase.client
                        .from("glyph_entitlements")
                        .select(Columns.raw("glyphs(id, name, strokes, view_box, user_id)")) {
                            order("created_at", Order.DESCENDING)
                        }.decodeList<EntitlementRow>()
                        .map { it.glyph }
                }
            withEntitled(ownAndSystem.await(), entitled.await())
        }

    companion object {
        private const val TAG = "glyph-service"

        /**
         * Own+system glyphs first (server order), then entitled glyphs not
         * already present. `cannot_buy_own` makes overlap impossible in
         * practice; the de-dupe is defensive.
         */
        fun withEntitled(
            base: List<Glyph>,
            entitled: List<Glyph>,
        ): List<Glyph> {
            val seen = base.mapTo(mutableSetOf()) { it.id }
            return base + entitled.filter { seen.add(it.id) }
        }
    }
}

/** Wire row for `glyph_entitlements` selects — PostgREST nests the joined glyph under `glyphs`. */
@Serializable
private data class EntitlementRow(
    @SerialName("glyphs")
    val glyph: Glyph,
)

val LocalGlyphService =
    staticCompositionLocalOf<GlyphService> {
        error("LocalGlyphService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
