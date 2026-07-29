package app.pbbls.android.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.OffsetDateTimeSerializer
import app.pbbls.android.features.path.models.PebbleDraftPayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.util.UUID

/** A `pebble_drafts` row as the drafts list needs it. */
@Serializable
data class PebbleDraftRecord(
    val id: String,
    val payload: PebbleDraftPayload,
    @SerialName("updated_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

/**
 * Server-side drafts (M47) — ports iOS `PebbleDraftsService.swift`.
 *
 * Direct RLS-scoped single-table calls (design D6, the sanctioned cross-surface
 * pattern): nothing here is multi-table and nothing emits karma. Errors
 * propagate to the caller, which owns loading/error view state.
 *
 * Zero karma is structural rather than enforced: `create_pebble` is the schema's
 * only `pebble_created` emitter and a draft never reaches it.
 */
class PebbleDraftsService(
    private val supabase: SupabaseService,
) {
    private companion object {
        const val TABLE = "pebble_drafts"
        const val COLUMNS = "id, payload, updated_at"
    }

    /**
     * Most recently saved first. `updated_at` is trigger-maintained server-side,
     * so the ordering does not depend on device clocks.
     */
    suspend fun list(): List<PebbleDraftRecord> =
        supabase.client
            .from(TABLE)
            .select(Columns.raw(COLUMNS)) {
                order("updated_at", Order.DESCENDING)
            }.decodeList()

    suspend fun load(id: String): PebbleDraftRecord? =
        supabase.client
            .from(TABLE)
            .select(Columns.raw(COLUMNS)) {
                filter { eq("id", id) }
                limit(1)
            }.decodeList<PebbleDraftRecord>()
            .firstOrNull()

    /**
     * Upsert. Passing an existing [id] replaces that draft's payload wholesale —
     * the point of the jsonb column, since a coalescing update could never clear
     * a field the user emptied.
     *
     * `user_id` is explicit because the RLS `with check` compares it to
     * `auth.uid()`.
     */
    suspend fun save(
        payload: PebbleDraftPayload,
        id: String?,
        userId: String,
    ): String {
        val rowId = id ?: UUID.randomUUID().toString()
        supabase.client
            .from(TABLE)
            .upsert(DraftUpsertPayload(id = rowId, userId = userId, payload = payload))
        return rowId
    }

    suspend fun delete(id: String) {
        supabase.client
            .from(TABLE)
            .delete {
                filter { eq("id", id) }
            }
    }

    /**
     * Count for the Path entry point. Selects `id` alone rather than reusing
     * [list]: this runs on every Path composition, and pulling every draft's full
     * jsonb payload to render one number is waste on the app's home screen.
     */
    suspend fun count(): Int =
        supabase.client
            .from(TABLE)
            .select(Columns.raw("id"))
            .decodeList<DraftIdRow>()
            .size

    /**
     * Whether a resumed draft's glyph is still usable (own ∪ system ∪ entitled).
     * Lives here rather than on `GlyphService` because draft hydration is its only
     * caller: `can_use_glyph` is the predicate `create_pebble` enforces, so
     * checking it while hydrating means publishing cannot fail on 42501 later
     * (design D7).
     */
    suspend fun canUseGlyph(
        glyphId: String,
        userId: String,
    ): Boolean =
        supabase.client.postgrest
            .rpc(
                "can_use_glyph",
                buildJsonObject {
                    put("p_glyph_id", glyphId)
                    put("p_user", userId)
                },
            ).decodeAs()
}

/**
 * `user_id` is explicitly encoded so the RLS `with check` sees it; `payload`
 * rides along as the nested jsonb.
 */
/** Minimal projection for the count query. */
@Serializable
private data class DraftIdRow(
    val id: String,
)

@Serializable
private data class DraftUpsertPayload(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val payload: PebbleDraftPayload,
)

val LocalPebbleDraftsService =
    staticCompositionLocalOf<PebbleDraftsService> {
        error("LocalPebbleDraftsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
