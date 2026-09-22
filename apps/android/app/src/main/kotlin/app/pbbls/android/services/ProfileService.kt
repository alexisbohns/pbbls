package app.pbbls.android.services

import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionRow
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.OffsetDateTimeSerializer
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The Profile surface's data seam — see [SupabaseServicing] for why these exist (#848). */
interface ProfileServicing {
    suspend fun loadProfile(): ProfileRow

    suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke>

    suspend fun loadCollections(): List<Collection>

    suspend fun saveSettings(
        displayName: String?,
        glyphId: String?,
        password: String?,
    )

    suspend fun setHandle(handle: String?)

    suspend fun setPublicProfile(isPublic: Boolean)

    suspend fun deleteAccount()
}

/**
 * Data access for the Profile surface — the fetch/save half of iOS
 * `ProfileView` + `SettingsSheet`, extracted into a service so the screens
 * stay previewable (the bootstrap convention keeps supabase-kt out of
 * composables). Errors propagate to the caller, which owns loading/error
 * view state (design D13).
 */
@Singleton
class ProfileService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : ProfileServicing {
        /** The signed-in user's `profiles` row (RLS-scoped single row). */
        override suspend fun loadProfile(): ProfileRow =
            supabase.client
                .from("profiles")
                .select(Columns.raw("display_name, created_at, glyph_id, handle, public_profile"))
                .decodeSingle()

        /** Stroke data for the profile glyph — mirrors `ProfileView.loadGlyphStrokes`. */
        override suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke> =
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
        override suspend fun loadCollections(): List<Collection> =
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
        override suspend fun saveSettings(
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

        /**
         * Claims, changes, or releases (null) the public handle — mirrors
         * `SettingsSheet.save()`'s `set_handle` call. The RPC normalizes and
         * validates, raising the stable codes `invalid_handle` / `handle_taken` /
         * `handle_reserved`; a null handle releases it and drops `public_profile`
         * in the same statement. Throws on failure; the screen maps the code.
         */
        override suspend fun setHandle(handle: String?) {
            supabase.client.postgrest.rpc(
                "set_handle",
                buildJsonObject {
                    if (handle == null) put("p_handle", JsonNull) else put("p_handle", handle)
                },
            )
        }

        /**
         * Flips the public-profile opt-in. A single-column owner-scoped write is
         * the sanctioned direct-client case (root `AGENTS.md`) — no RPC. The DB
         * CHECK rejects `true` without a handle, so callers claim first.
         */
        override suspend fun setPublicProfile(isPublic: Boolean) {
            val userId = supabase.session?.user?.id ?: error("not authenticated")
            supabase.client
                .from("profiles")
                .update(buildJsonObject { put("public_profile", isPublic) }) {
                    filter { eq("user_id", userId) }
                }
        }

        /**
         * Invokes the `delete-account` edge function, which purges the row graph and
         * the auth user. Throws on failure; the caller signs out and maps the error.
         *
         * Lives here rather than on the screen (#848) so `SupabaseServicing` never
         * has to expose the raw client — a client on that interface would make every
         * fake of it pointless.
         */
        override suspend fun deleteAccount() {
            supabase.client.functions.invoke("delete-account")
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
    val handle: String? = null,
    @SerialName("public_profile")
    val publicProfile: Boolean = false,
)
