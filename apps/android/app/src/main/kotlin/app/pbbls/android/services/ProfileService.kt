package app.pbbls.android.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.models.OffsetDateTimeSerializer
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionRow
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

/**
 * Data access for the Profile surface — the fetch/save half of iOS
 * `ProfileView` + `SettingsSheet`, extracted into a service so the screens
 * stay previewable (the bootstrap convention keeps supabase-kt out of
 * composables). Errors propagate to the caller, which owns loading/error
 * view state (design D13).
 */
class ProfileService(
    private val supabase: SupabaseService,
) {
    /** The signed-in user's `profiles` row (RLS-scoped single row). */
    suspend fun loadProfile(): ProfileRow =
        supabase.client
            .from("profiles")
            .select(Columns.raw("display_name, created_at, glyph_id"))
            .decodeSingle()

    /** Stroke data for the profile glyph — mirrors `ProfileView.loadGlyphStrokes`. */
    suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke> =
        supabase.client
            .from("glyphs")
            .select(Columns.raw("strokes")) {
                filter { eq("id", glyphId) }
            }.decodeSingle<GlyphStrokesRow>()
            .strokes

    /**
     * Collections with their live pebble counts for the profile carousel —
     * mirrors `ProfileCollectionsCard.load()`, newest first.
     */
    suspend fun loadCollections(): List<Collection> =
        supabase.client
            .from("collections")
            .select(Columns.raw("id, name, mode, pebble_count:collection_pebbles(count)")) {
                order("created_at", Order.DESCENDING)
            }.decodeList<CollectionRow>()
            .map { it.toCollection() }

    /**
     * Saves the Settings form — mirrors `SettingsSheet.save()`: only-changed
     * fields, `update_profile` RPC for name/glyph (absent keys mean "don't
     * change"; the RPC cannot clear glyph_id by design), then the GoTrue
     * password update. Throws on failure; the screen maps to its inline error.
     */
    suspend fun saveSettings(
        displayName: String?,
        glyphId: String?,
        password: String?,
    ) {
        if (displayName != null || glyphId != null) {
            supabase.client.postgrest.rpc(
                "update_profile",
                buildJsonObject {
                    displayName?.let { put("p_display_name", it) }
                    glyphId?.let { put("p_glyph_id", it) }
                },
            )
        }
        if (password != null) {
            supabase.client.auth.updateUser {
                this.password = password
            }
        }
    }

    @Serializable
    private data class GlyphStrokesRow(
        val strokes: List<GlyphStroke>,
    )
}

/** The signed-in user's `profiles` row — the iOS `ProfileRow` analog. */
@Serializable
data class ProfileRow(
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @SerialName("glyph_id")
    val glyphId: String? = null,
)

/** CompositionLocal for [ProfileService] — see [LocalSupabaseService] (D4). */
val LocalProfileService =
    staticCompositionLocalOf<ProfileService> {
        error("LocalProfileService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
