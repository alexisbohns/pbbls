package app.pbbls.android.features.glyph.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Glyph CRUD — the iOS `GlyphService` analog: the attachable-glyph list
 * (D13), plus M43's carve insert and rename. Market reads/buys live in
 * `GlyphMarketService` (M43 design D9). Methods throw to the caller, which
 * owns view state (keeps the seam JVM-testable). Mirrors
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

    /**
     * Carve insert — ports `GlyphService.create`: exactly four keys
     * (`user_id`, `strokes`, literal `view_box`, `name` as string or explicit
     * JSON null — never a shape key, #503), select-back so the fresh glyph
     * lands in pickers without a refetch.
     */
    suspend fun create(
        strokes: List<GlyphStroke>,
        name: String?,
    ): Glyph {
        val userId =
            supabase.session?.user?.id
                ?: throw IllegalStateException("glyph save without session")
        return supabase.client
            .from("glyphs")
            .insert(
                buildJsonObject {
                    put("user_id", userId)
                    put("strokes", Json.encodeToJsonElement(ListSerializer(GlyphStroke.serializer()), strokes))
                    put("view_box", "0 0 200 200")
                    put("name", normalizedName(name))
                },
            ) {
                select(Columns.raw("id, name, strokes, view_box, user_id"))
            }.decodeSingle<Glyph>()
    }

    /** Rename — empty/whitespace input CLEARS the name (explicit null; M43 D8). */
    suspend fun updateName(
        glyphId: String,
        name: String?,
    ): Glyph =
        supabase.client
            .from("glyphs")
            .update(
                buildJsonObject { put("name", normalizedName(name)) },
            ) {
                filter { eq("id", glyphId) }
                select(Columns.raw("id, name, strokes, view_box, user_id"))
            }.decodeSingle<Glyph>()

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

        /** iOS name normalization: trim; empty → null (JVM-tested). */
        fun normalizedName(name: String?): String? = name?.trim()?.takeIf { it.isNotEmpty() }
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
