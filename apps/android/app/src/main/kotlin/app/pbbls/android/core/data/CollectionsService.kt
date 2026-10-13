package app.pbbls.android.core.data

import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.CollectionRow
import app.pbbls.android.core.model.collectionInsertPayload
import app.pbbls.android.core.model.collectionUpdatePayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION_COLUMNS =
    "id, name, mode, glyph_id, glyphs(id, name, strokes, view_box), pebble_count:collection_pebbles(count)"

/**
 * The collections seam (#849) — what [CollectionsListViewModel],
 * [CollectionDetailViewModel] and [CollectionFormViewModel] are tested against.
 *
 * Same bar as [SoulsServicing]: extracted because those three tests need it.
 */
interface CollectionsServicing {
    suspend fun list(): List<Collection>

    suspend fun loadCollection(collectionId: String): Collection

    suspend fun create(
        name: String,
        mode: CollectionMode?,
        glyphId: String?,
    )

    suspend fun update(
        collectionId: String,
        name: String,
        mode: CollectionMode?,
        glyphId: String?,
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
                .select(Columns.raw(COLLECTION_COLUMNS)) {
                    order("name", Order.ASCENDING)
                }.decodeList<CollectionRow>()
                .map { it.toCollection() }

        /** One collection with its live count — the detail header (re)load. */
        override suspend fun loadCollection(collectionId: String): Collection =
            supabase.client
                .from("collections")
                .select(Columns.raw(COLLECTION_COLUMNS)) {
                    filter { eq("id", collectionId) }
                }.decodeSingle<CollectionRow>()
                .toCollection()

        /** Create with optional mode and glyph — no select-back; callers reload (iOS parity). */
        override suspend fun create(
            name: String,
            mode: CollectionMode?,
            glyphId: String?,
        ) {
            val userId =
                supabase.session?.user?.id
                    ?: throw IllegalStateException("createCollection: no session")
            supabase.client
                .from("collections")
                .insert(collectionInsertPayload(userId = userId, name = name, mode = mode, glyphId = glyphId))
        }

        /** Update name, mode and glyph — an explicit JSON null clears either column. */
        override suspend fun update(
            collectionId: String,
            name: String,
            mode: CollectionMode?,
            glyphId: String?,
        ) {
            supabase.client
                .from("collections")
                .update(collectionUpdatePayload(name = name, mode = mode, glyphId = glyphId)) {
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
