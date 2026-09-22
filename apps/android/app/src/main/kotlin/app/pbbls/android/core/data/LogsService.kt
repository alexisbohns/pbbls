package app.pbbls.android.core.data

import app.pbbls.android.AppEnvironment
import app.pbbls.android.core.model.LabConfig
import app.pbbls.android.core.model.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "logs-service"

/**
 * The Lab seam (#849) — what [app.pbbls.android.features.lab.LabViewModel] and
 * [app.pbbls.android.features.lab.LogListViewModel] are tested against.
 */
interface LogsServicing {
    suspend fun announcements(limit: Int? = null): List<Log>

    /**
     * One row by [id], or null if it does not exist. A single-table,
     * single-statement read, so no RPC (root `AGENTS.md`) — the seam
     * [AnnouncementDetailViewModel] loads by id, mirroring how
     * [app.pbbls.android.features.profile.SoulDetailViewModel] fetches its
     * soul rather than receiving the row from whichever screen had it.
     */
    suspend fun log(id: String): Log?

    suspend fun changelog(limit: Int? = null): List<Log>

    suspend fun initiatives(): List<Log>

    suspend fun backlog(limit: Int? = null): List<Log>

    suspend fun myReactions(): Set<String>

    suspend fun react(logId: String)

    suspend fun unreact(logId: String)

    /**
     * Not a call — a pure projection of [Log.coverImagePath] against the project
     * URL. On the interface so the ViewModel can fold it into state and the
     * screen never reads a service; a fake supplies its own base rather than
     * touching `AppEnvironment`.
     */
    fun coverImageUrl(log: Log): String?
}

/**
 * The Lab data layer — ports iOS `LogsService` (M44 design D3/D4). Four feed
 * reads over `v_logs_with_counts` (decoded lossily via [LossyLogList]) and the
 * reaction writes, which go straight to `log_reactions` — a single-table,
 * single-statement insert/delete on PK `(log_id, user_id)`, the sanctioned
 * direct-write pattern (deliberately no RPC, per root `AGENTS.md`; iOS
 * comments the same). Methods throw; screens own view state and the
 * optimistic revert.
 */

@Singleton
class LogsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : LogsServicing {
        /** Published announcements, newest published first. */
        override suspend fun announcements(limit: Int?): List<Log> =
            feed {
                filter {
                    eq("species", "announcement")
                    eq("published", true)
                }
                order("published_at", Order.DESCENDING)
                limit?.let { limit(it.toLong()) }
            }

        /** One row by [id], undecorated by any species/status filter — see the interface doc. */
        override suspend fun log(id: String): Log? =
            supabase.client
                .from("v_logs_with_counts")
                .select {
                    filter { eq("id", id) }
                }.decodeSingleOrNull<Log>()

        /**
         * Shipped features — `released_at` DESC with nulls LAST (the easy-to-miss
         * D3 detail), then `published_at` DESC. The client-side re-sort below
         * guarantees the rendered order matches iOS even if the query builder
         * collapses the two `order` calls; only a `limit` truncation under tied
         * timestamps could theoretically differ (design risk 3).
         */
        override suspend fun changelog(limit: Int?): List<Log> =
            feed {
                filter {
                    eq("species", "feature")
                    eq("status", "shipped")
                    eq("published", true)
                }
                order("released_at", Order.DESCENDING, nullsFirst = false)
                order("published_at", Order.DESCENDING)
                limit?.let { limit(it.toLong()) }
            }.sortedWith(changelogOrder)

        /** Features in progress, newest published first — always unlimited (iOS). */
        override suspend fun initiatives(): List<Log> =
            feed {
                filter {
                    eq("species", "feature")
                    eq("status", "in_progress")
                    eq("published", true)
                }
                order("published_at", Order.DESCENDING)
            }

        /** Backlog features — most upvoted first, then newest created. */
        override suspend fun backlog(limit: Int?): List<Log> =
            feed {
                filter {
                    eq("species", "feature")
                    eq("status", "backlog")
                    eq("published", true)
                }
                order("reaction_count", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit?.let { limit(it.toLong()) }
            }.sortedWith(backlogOrder)

        /**
         * The caller's reacted log ids. No session → empty set WITHOUT throwing
         * (iOS: an anonymous Lab renders with zero reactions); the writes below
         * DO throw without a session.
         */
        override suspend fun myReactions(): Set<String> {
            val me = supabase.session?.user?.id ?: return emptySet()
            return supabase.client
                .from("log_reactions")
                .select(Columns.list("log_id")) {
                    filter { eq("user_id", me) }
                }.decodeList<ReactionRow>()
                .map { it.logId }
                .toSet()
        }

        /**
         * Upvote — inserts the `(log_id, user_id)` row. A duplicate insert
         * conflicts on the PK and throws like any failure, triggering the
         * caller's optimistic revert.
         */
        override suspend fun react(logId: String) {
            val me = requireUserId()
            try {
                supabase.client
                    .from("log_reactions")
                    .insert(ReactionWrite(logId = logId, userId = me))
            } catch (e: Exception) {
                android.util.Log.e(TAG, "react failed", e)
                throw e
            }
        }

        /** Remove an upvote — deletes by both PK columns. */
        override suspend fun unreact(logId: String) {
            val me = requireUserId()
            try {
                supabase.client
                    .from("log_reactions")
                    .delete {
                        filter {
                            eq("log_id", logId)
                            eq("user_id", me)
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "unreact failed", e)
                throw e
            }
        }

        /** Public cover-image URL for [log], or null (design D7). */
        override fun coverImageUrl(log: Log): String? = LabConfig.coverImageUrl(AppEnvironment.supabaseUrl, log.coverImagePath)

        // android.util.Log stays fully qualified in this file — the imported Log
        // is the Lab model.
        private suspend fun feed(builder: PostgrestRequestBuilder.() -> Unit): List<Log> {
            val raw =
                supabase.client
                    .from("v_logs_with_counts")
                    .select(request = builder)
                    .data
            return LossyLogList.decode(raw) { index, cause ->
                android.util.Log.e(TAG, "skipped log row $index", cause)
            }
        }

        private fun requireUserId(): String =
            supabase.session?.user?.id
                ?: throw IllegalStateException("lab reaction without session")

        @Serializable
        private data class ReactionRow(
            @SerialName("log_id")
            val logId: String,
        )

        @Serializable
        private data class ReactionWrite(
            @SerialName("log_id")
            val logId: String,
            @SerialName("user_id")
            val userId: String,
        )

        companion object {
            /** iOS changelog order: released_at DESC nulls last, then published_at DESC. */
            internal val changelogOrder: Comparator<Log> =
                compareByDescending<Log> { it.releasedAt != null }
                    .thenByDescending { it.releasedAt ?: OffsetDateTime.MIN }
                    .thenByDescending { it.publishedAt ?: OffsetDateTime.MIN }

            /** iOS backlog order: reaction_count DESC, then created_at DESC. */
            internal val backlogOrder: Comparator<Log> =
                compareByDescending<Log> { it.reactionCount }
                    .thenByDescending { it.createdAt }
        }
    }
