package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/** Every moment read (#1048). Extracted because the Path, detail, edit and list ViewModels have tests. */
interface MomentReadServicing {
    suspend fun loadPath(): List<PathMoment>

    suspend fun loadMoment(momentId: String): MomentDetail

    suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail>

    suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail>

    suspend fun resolveGlyph(pebbleId: String): PebbleGlyph?
}

/**
 * `path_moments()` for the timeline and `v_moments_full` for everything that
 * needs a whole moment. Single-statement reads, so direct calls (root AGENTS.md).
 * Errors propagate; callers own their loading and error state.
 */
@Singleton
class MomentReadService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : MomentReadServicing {
        override suspend fun loadPath(): List<PathMoment> {
            val moments =
                supabase.client.postgrest
                    .rpc("path_moments")
                    .decodeList<PathMoment>()
            // Valence.fromOrDefault is pure and log-free; decode drift is surfaced here, once per load.
            moments
                .mapNotNull { it.pebble }
                .filter { p -> Valence.entries.none { it.positiveness == p.positiveness && it.intensity == p.intensity } }
                .forEach { Log.e(TAG, "unexpected valence (${it.positiveness}, ${it.intensity}) on pebble ${it.id}") }
            return moments
        }

        override suspend fun loadMoment(momentId: String): MomentDetail =
            supabase.client
                .from(VIEW)
                .select(Columns.raw(COLUMNS)) { filter { eq("id", momentId) } }
                .decodeSingle()

        override suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail> = listContaining(withSoul(soulId))

        override suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail> = listContaining(inCollection(collectionId))

        override suspend fun resolveGlyph(pebbleId: String): PebbleGlyph? =
            supabase.client.postgrest
                .rpc("resolve_pebble_glyph", buildJsonObject { put("p_pebble_id", pebbleId) })
                .decodeAs<PebbleGlyph?>()

        /**
         * `pebble @> {...}`: only the caller's own pebble is non-null in the
         * view, so the containment also restricts to the caller's moments.
         */
        private suspend fun listContaining(containment: String): List<MomentDetail> =
            supabase.client
                .from(VIEW)
                .select(Columns.raw(COLUMNS)) {
                    filter { filter("pebble", FilterOperator.CS, containment) }
                    order("happened_at", Order.DESCENDING)
                }.decodeList()

        companion object {
            private const val TAG = "moments"
            private const val VIEW = "v_moments_full"
            internal const val COLUMNS = "id, name, description, happened_at, created_at, snaps, pebble"

            internal fun withSoul(soulId: String): String =
                buildJsonObject { putJsonArray("souls") { addJsonObject { put("id", soulId) } } }.toString()

            internal fun inCollection(collectionId: String): String =
                buildJsonObject { putJsonArray("collections") { addJsonObject { put("id", collectionId) } } }.toString()
        }
    }
