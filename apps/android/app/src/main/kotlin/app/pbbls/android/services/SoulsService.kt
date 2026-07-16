package app.pbbls.android.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.SoulRow
import app.pbbls.android.features.profile.models.SoulWithGlyph
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Data access for the souls management surfaces (sub-project D) — the
 * fetch/write half of iOS `SoulDetailView` + `Create/EditSoulSheet`,
 * extracted so screens stay previewable. All writes are direct RLS-scoped
 * single-table calls (design D6 — the sanctioned cross-surface pattern; the
 * `souls_glyph_usable` trigger owns glyph-ownership enforcement server-side).
 * Errors propagate to the caller, which owns loading/error view state.
 */
class SoulsService(
    private val supabase: SupabaseService,
) {
    /** All souls, name-ascending — the `SoulsListView.load()` analog. */
    suspend fun list(): List<SoulWithGlyph> =
        supabase.client
            .from("souls")
            .select(
                Columns.raw("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"),
            ) {
                order("name", Order.ASCENDING)
            }.decodeList<SoulRow>()
            .map { it.toSoulWithGlyph() }

    /** One soul with its glyph + live count — the detail header reload. */
    suspend fun loadSoul(soulId: String): SoulWithGlyph =
        supabase.client
            .from("souls")
            .select(
                Columns.raw("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"),
            ) {
                filter { eq("id", soulId) }
            }.decodeSingle<SoulRow>()
            .toSoulWithGlyph()

    /**
     * Pebbles tagged with the soul, newest first — mirrors
     * `SoulDetailView.load()`'s `pebble_souls!inner` embedded filter.
     */
    suspend fun loadPebbles(soulId: String): List<Pebble> =
        supabase.client
            .from("pebbles")
            .select(
                Columns.raw(
                    "id, name, happened_at, created_at, intensity, positiveness, render_svg, " +
                        "emotion:emotions(id, slug, name), pebble_souls!inner(soul_id)",
                ),
            ) {
                filter { eq("pebble_souls.soul_id", soulId) }
                order("happened_at", Order.DESCENDING)
            }.decodeList<Pebble>()

    /** Full-form create (name + glyph) — the `SoulInsertPayload` analog with select-back. */
    suspend fun create(
        name: String,
        glyphId: String,
    ): SoulWithGlyph {
        val userId =
            supabase.session?.user?.id
                ?: throw IllegalStateException("createSoul: no session")
        return supabase.client
            .from("souls")
            .insert(
                buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                    put("glyph_id", glyphId)
                },
            ) {
                select(
                    Columns.raw(
                        "id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)",
                    ),
                )
            }.decodeSingle<SoulRow>()
            .toSoulWithGlyph()
    }

    /** Update name + glyph — the `SoulUpdatePayload` analog. */
    suspend fun update(
        soulId: String,
        name: String,
        glyphId: String,
    ) {
        supabase.client
            .from("souls")
            .update(
                buildJsonObject {
                    put("name", name)
                    put("glyph_id", glyphId)
                },
            ) {
                filter { eq("id", soulId) }
            }
    }

    /**
     * Delete — linked pebbles stay; `pebble_souls.soul_id` cascades
     * server-side so only the links are removed.
     */
    suspend fun delete(soulId: String) {
        supabase.client
            .from("souls")
            .delete {
                filter { eq("id", soulId) }
            }
    }

    /** Glyph-by-id fetch for the form's thumbnail after a picker selection. */
    suspend fun loadGlyph(glyphId: String): Glyph =
        supabase.client
            .from("glyphs")
            .select(Columns.raw("id, name, strokes, view_box")) {
                filter { eq("id", glyphId) }
            }.decodeSingle()
}

/** CompositionLocal for [SoulsService] — see [LocalSupabaseService] (D4). */
val LocalSoulsService =
    staticCompositionLocalOf<SoulsService> {
        error("LocalSoulsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
