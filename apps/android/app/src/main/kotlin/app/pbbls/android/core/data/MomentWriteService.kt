package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.MomentWireJson
import app.pbbls.android.core.model.PebbleCompletePayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Every moment and pebble write (#1048). Throws; callers map with `toDataError()` + `momentWriteErrorRes`. */
interface MomentWriteServicing {
    /** `save_moment`; returns the moment id. */
    suspend fun saveMoment(payload: MomentSavePayload): String

    /** `complete_pebble`; returns the pebble id. Does not check achievements (A-7). */
    suspend fun completePebble(payload: PebbleCompletePayload): String

    /** `delete_pebble`: the pebble only, the moment stays. Takes a PEBBLE id. */
    suspend fun deletePebble(pebbleId: String)

    /** `delete_moment`, then a best-effort delete of the returned snap files; clears the moment's Complete autosave. */
    suspend fun deleteMoment(momentId: String)

    /**
     * The sum of every `karma_events.delta` for [pebbleId] (all reasons), or null
     * when the read fails. Never throws. Edit pebble reads it either side of
     * `complete_pebble`: the difference is what that save earned (R13).
     */
    suspend fun karmaTotal(pebbleId: String): Int?

    /** The delta the last write recorded for [pebbleId], or null. Never throws. */
    suspend fun karmaDelta(
        pebbleId: String,
        reason: KarmaReason,
    ): Int?
}

@Singleton
class MomentWriteService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val snapFiles: SnapWriteRepositing,
        private val completeSnapshots: CompleteSnapshotStoring,
    ) : MomentWriteServicing {
        override suspend fun saveMoment(payload: MomentSavePayload): String =
            supabase.client.postgrest
                .rpc(
                    "save_moment",
                    buildJsonObject { put("payload", MomentWireJson.encodeToJsonElement(MomentSavePayload.serializer(), payload)) },
                ).decodeAs<String>()

        override suspend fun completePebble(payload: PebbleCompletePayload): String =
            supabase.client.postgrest
                .rpc(
                    "complete_pebble",
                    buildJsonObject { put("payload", MomentWireJson.encodeToJsonElement(PebbleCompletePayload.serializer(), payload)) },
                ).decodeAs<String>()

        override suspend fun deletePebble(pebbleId: String) {
            supabase.client.postgrest.rpc("delete_pebble", buildJsonObject { put("p_pebble_id", pebbleId) })
        }

        override suspend fun deleteMoment(momentId: String) {
            val result =
                supabase.client.postgrest
                    .rpc("delete_moment", buildJsonObject { put("p_moment_id", momentId) })
                    .decodeAs<DeleteMomentResult>()
            // A half-finished completion of a deleted moment can never be resumed (A-5).
            completeSnapshots.clear(momentId)
            // The rows are gone server-side; the files must follow even if the
            // caller leaves. Best-effort: the orphan sweep is the backstop.
            withContext(NonCancellable) {
                result.storagePaths.forEach { snapFiles.deleteFiles(storagePrefix = it) }
            }
        }

        override suspend fun karmaTotal(pebbleId: String): Int? =
            runCatchingCancellable {
                supabase.client
                    .from("karma_events")
                    .select(Columns.raw("delta")) {
                        filter { eq("ref_id", pebbleId) }
                    }.decodeList<KarmaDeltaRow>()
                    .sumOf { it.delta }
            }.onFailure { Log.w(TAG, "karma total read failed for $pebbleId", it) }
                .getOrNull()

        /** Mirrors `functions/_shared/karma-delta.ts`: newest row for (ref_id, reason). */
        override suspend fun karmaDelta(
            pebbleId: String,
            reason: KarmaReason,
        ): Int? =
            runCatchingCancellable {
                supabase.client
                    .from("karma_events")
                    .select(Columns.raw("delta")) {
                        filter {
                            eq("ref_id", pebbleId)
                            eq("reason", reason.wire())
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeList<KarmaDeltaRow>()
                    .firstOrNull()
                    ?.delta
            }.onFailure { Log.w(TAG, "karma read-back failed for $pebbleId", it) }
                .getOrNull()

        companion object {
            private const val TAG = "moment-write"

            internal fun KarmaReason.wire(): String =
                when (this) {
                    KarmaReason.PEBBLE_CREATED -> "pebble_created"
                    KarmaReason.PEBBLE_ENRICHED -> "pebble_enriched"
                }
        }
    }

@Serializable
internal data class DeleteMomentResult(
    @SerialName("storage_paths")
    val storagePaths: List<String> = emptyList(),
)

@Serializable
private data class KarmaDeltaRow(
    val delta: Int,
)
