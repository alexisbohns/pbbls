package app.pbbls.android.features.lab.services

import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.AppEnvironment
import app.pbbls.android.features.lab.models.LabConfig
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.services.SupabaseService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

private const val TAG = "logs-service"

/**
 * The Lab data layer — ports iOS `LogsService` (M44 design D3/D4). Four feed
 * reads over `v_logs_with_counts` (decoded lossily via [LossyLogList]) and the
 * reaction writes, which go straight to `log_reactions` — a single-table,
 * single-statement insert/delete on PK `(log_id, user_id)`, the sanctioned
 * direct-write pattern (deliberately no RPC, per root `AGENTS.md`; iOS
 * comments the same). Methods throw; screens own view state and the
 * optimistic revert.
 */
class LogsService(
    private val supabase: SupabaseService,
) {
    /** Published announcements, newest published first. */
    suspend fun announcements(limit: Int? = null): List<Log> =
        feed {
            filter {
                eq("species", "announcement")
                eq("published", true)
            }
            order("published_at", Order.DESCENDING)
            limit?.let { limit(it.toLong()) }
        }

    /**
     * Shipped features — `released_at` DESC with nulls LAST (the easy-to-miss
     * D3 detail), then `published_at` DESC. The client-side re-sort below
     * guarantees the rendered order matches iOS even if the query builder
     * collapses the two `order` calls; only a `limit` truncation under tied
     * timestamps could theoretically differ (design risk 3).
     */
    suspend fun changelog(limit: Int? = null): List<Log> =
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
    suspend fun initiatives(): List<Log> =
        feed {
            filter {
                eq("species", "feature")
                eq("status", "in_progress")
                eq("published", true)
            }
            order("published_at", Order.DESCENDING)
        }

    /** Backlog features — most upvoted first, then newest created. */
    suspend fun backlog(limit: Int? = null): List<Log> =
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
    suspend fun myReactions(): Set<String> {
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
    suspend fun react(logId: String) {
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
    suspend fun unreact(logId: String) {
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
    fun coverImageUrl(log: Log): String? = LabConfig.coverImageUrl(AppEnvironment.supabaseUrl, log.coverImagePath)

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

/** CompositionLocal for [LogsService] — see [LocalSupabaseService] (D4). */
val LocalLogsService =
    staticCompositionLocalOf<LogsService> {
        error("LocalLogsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
