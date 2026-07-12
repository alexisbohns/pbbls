package app.pbbls.android.features.glyph.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * Lists glyphs visible to the current user (RLS exposes own + system +
 * entitled) — the `GlyphService.list()` analog (D13). Selection-only in this
 * milestone; no carve/create. `list()` throws to the caller — the glyph
 * picker's `LaunchedEffect` catches and logs (keeps the seam JVM-testable).
 * Mirrors ReferenceDataService.fetchSouls's supabase-kt call shape.
 */
class GlyphService(
    private val supabase: SupabaseService,
) {
    suspend fun list(): List<Glyph> =
        supabase.client
            .from("glyphs")
            .select(Columns.raw("id, name, strokes, view_box, user_id")) {
                order("created_at", Order.DESCENDING)
            }.decodeList<Glyph>()

    companion object {
        private const val TAG = "glyph-service"
    }
}

val LocalGlyphService =
    staticCompositionLocalOf<GlyphService> {
        error("LocalGlyphService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
