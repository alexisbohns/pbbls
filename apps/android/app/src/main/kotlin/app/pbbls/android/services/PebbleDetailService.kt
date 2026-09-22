package app.pbbls.android.services

import app.pbbls.android.core.model.PebbleDetail
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single-pebble read seam (#849). Extracted because the detail and edit
 * ViewModels have tests, which is the standing bar for an interface here.
 */
interface PebbleDetailServicing {
    suspend fun load(pebbleId: String): PebbleDetail
}

/**
 * Loads a single pebble with its embedded relations for the detail/edit
 * surfaces — the `PebbleDetailSheet.load()` analog (D7). Direct PostgREST
 * embedded select, NOT an RPC: the join graph is read-only and single-request,
 * so an RPC would add nothing (root AGENTS.md: single read -> direct client call
 * is fine). Errors propagate; the caller owns loading/error view state.
 */
@Singleton
class PebbleDetailService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : PebbleDetailServicing {
        override suspend fun load(pebbleId: String): PebbleDetail =
            supabase.client
                .from("pebbles")
                .select(Columns.raw(DETAIL_SELECT)) {
                    filter { eq("id", pebbleId) }
                }.decodeSingle<PebbleDetail>()

        companion object {
            // Mirror of PebbleDetailSheet.load()'s select, plus the top-level
            // glyph:glyphs(...) embed for D's edit prefill (PebbleDetail.glyph).
            // Newlines collapsed to spaces (PostgREST tolerates the spaces, as it
            // does for the iOS client).
            private val DETAIL_SELECT =
                """
                id, name, description, happened_at, intensity, positiveness, visibility,
                render_svg, render_version, glyph_id,
                glyph:glyphs(id, name, strokes, view_box),
                emotion:emotions(id, slug, name),
                pebble_domains(domain:domains(id, slug, name)),
                pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
                collection_pebbles(collection:collections(id, name)),
                snaps(id, storage_path, sort_order)
                """.trimIndent().replace("\n", " ")
        }
    }
