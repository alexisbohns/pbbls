package app.pbbls.android.features.glyph.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

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
     * The current user's own glyphs plus system glyphs (`user_id is null`). The
     * `glyphs` RLS SELECT was widened for the marketplace to ALSO expose approved
     * community submissions (browsable, but not owned), so a bare select would
     * let the picker attach a community glyph the user doesn't own. The explicit
     * own+system filter prevents that. Purchased (entitled) glyphs are out of
     * scope until the marketplace ships on Android.
     */
    suspend fun list(): List<Glyph> {
        val me = supabase.session?.user?.id
        return supabase.client
            .from("glyphs")
            .select(Columns.raw("id, name, strokes, view_box, user_id")) {
                order("created_at", Order.DESCENDING)
            }.decodeList<Glyph>()
            .filter { it.userId == null || it.userId == me }
    }

    companion object {
        private const val TAG = "glyph-service"
    }
}

val LocalGlyphService =
    staticCompositionLocalOf<GlyphService> {
        error("LocalGlyphService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
