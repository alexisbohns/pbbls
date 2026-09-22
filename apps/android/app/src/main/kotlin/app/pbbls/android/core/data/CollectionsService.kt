package app.pbbls.android.core.data

import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.CollectionRow
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.collectionInsertPayload
import app.pbbls.android.core.model.collectionUpdatePayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The collections seam (#849) — what [CollectionsListViewModel],
 * [CollectionDetailViewModel] and [CollectionFormViewModel] are tested against.
 *
 * Same bar as [SoulsServicing]: extracted because those three tests need it.
 */
interface CollectionsServicing {
    suspend fun list(): List<Collection>

    suspend fun loadCollection(collectionId: String): Collection

    suspend fun loadPebbles(collectionId: String): List<Pebble>

    suspend fun create(
        name: String,
        mode: CollectionMode?,
    )

    suspend fun update(
        collectionId: String,
        name: String,
        mode: CollectionMode?,
    )

    suspend fun delete(collectionId: String)
}

/**
 * Data access for the collections management surfaces (sub-project E) — the
 * fetch/write half of iOS `CollectionsListView` + `CollectionDetailView` +
 * `Create/EditCollectionSheet`, extracted so screens stay previewable. All
 * writes are direct RLS-scoped single-table calls (design D6 — the sanctioned
 * cross-surface pattern). Errors propagate to the caller, which owns
 * loading/error view state.
 */
@Singleton
class CollectionsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : CollectionsServicing {
        /** All collections, name-ascending — the `CollectionsListView.load()` analog. */
        override suspend fun list(): List<Collection> =
            supabase.client
                .from("collections")
                .select(Columns.raw("id, name, mode, pebble_count:collection_pebbles(count)")) {
                    order("name", Order.ASCENDING)
                }.decodeList<CollectionRow>()
                .map { it.toCollection() }

        /** One collection with its live count — the detail header (re)load. */
        override suspend fun loadCollection(collectionId: String): Collection =
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
        override suspend fun loadPebbles(collectionId: String): List<Pebble> =
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
        override suspend fun create(
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
        override suspend fun update(
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
        override suspend fun delete(collectionId: String) {
            supabase.client
                .from("collections")
                .delete {
                    filter { eq("id", collectionId) }
                }
        }
    }
