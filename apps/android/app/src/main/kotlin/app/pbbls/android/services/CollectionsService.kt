package app.pbbls.android.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.features.profile.models.CollectionRow
import app.pbbls.android.features.profile.models.collectionInsertPayload
import app.pbbls.android.features.profile.models.collectionUpdatePayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * Data access for the collections management surfaces (sub-project E) — the
 * fetch/write half of iOS `CollectionsListView` + `CollectionDetailView` +
 * `Create/EditCollectionSheet`, extracted so screens stay previewable. All
 * writes are direct RLS-scoped single-table calls (design D6 — the sanctioned
 * cross-surface pattern). Errors propagate to the caller, which owns
 * loading/error view state.
 */
class CollectionsService(
    private val supabase: SupabaseService,
) {
    /** All collections, name-ascending — the `CollectionsListView.load()` analog. */
    suspend fun list(): List<Collection> =
        supabase.client
            .from("collections")
            .select(Columns.raw("id, name, mode, pebble_count:collection_pebbles(count)")) {
                order("name", Order.ASCENDING)
            }.decodeList<CollectionRow>()
            .map { it.toCollection() }

    /** One collection with its live count — the detail header (re)load. */
    suspend fun loadCollection(collectionId: String): Collection =
        supabase.client
            .from("collections")
            .select(Columns.raw("id, name, mode, pebble_count:collection_pebbles(count)")) {
                filter { eq("id", collectionId) }
            }.decodeSingle<CollectionRow>()
            .toCollection()

    /**
     * Pebbles in the collection, newest first — mirrors
     * `CollectionDetailView.load()`'s `collection_pebbles!inner` embedded
     * filter (note the junction's word order, opposite of `pebble_souls`).
     */
    suspend fun loadPebbles(collectionId: String): List<Pebble> =
        supabase.client
            .from("pebbles")
            .select(
                Columns.raw(
                    "id, name, happened_at, created_at, intensity, positiveness, render_svg, " +
                        "emotion:emotions(id, slug, name), collection_pebbles!inner(collection_id)",
                ),
            ) {
                filter { eq("collection_pebbles.collection_id", collectionId) }
                order("happened_at", Order.DESCENDING)
            }.decodeList<Pebble>()

    /** Create with optional mode — no select-back; callers reload (iOS parity). */
    suspend fun create(
        name: String,
        mode: CollectionMode?,
    ) {
        val userId =
            supabase.session?.user?.id
                ?: throw IllegalStateException("createCollection: no session")
        supabase.client
            .from("collections")
            .insert(collectionInsertPayload(userId = userId, name = name, mode = mode))
    }

    /** Update name + mode — explicit JSON-null mode clears the column. */
    suspend fun update(
        collectionId: String,
        name: String,
        mode: CollectionMode?,
    ) {
        supabase.client
            .from("collections")
            .update(collectionUpdatePayload(name = name, mode = mode)) {
                filter { eq("id", collectionId) }
            }
    }

    /**
     * Delete — linked pebbles stay; `collection_pebbles.collection_id`
     * cascades server-side so only the links are removed.
     */
    suspend fun delete(collectionId: String) {
        supabase.client
            .from("collections")
            .delete {
                filter { eq("id", collectionId) }
            }
    }
}

/** CompositionLocal for [CollectionsService] — see [LocalSupabaseService] (D4). */
val LocalCollectionsService =
    staticCompositionLocalOf<CollectionsService> {
        error("LocalCollectionsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
