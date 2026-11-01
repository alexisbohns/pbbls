package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.core.model.home.HomeWrapMonth
import app.pbbls.android.core.model.home.decodeGuides
import app.pbbls.android.core.model.home.decodeHomeWraps
import app.pbbls.android.core.model.home.decodeRevealedAt
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "home-service"

/**
 * Home's server reads (#1383; iOS `HomeServicing`), the seam Home's ViewModel is tested against.
 * Nothing here aggregates: every number comes from an RPC or a table the server owns. Methods throw,
 * except [displayName]; callers map failures with `toDataError()`.
 */
interface HomeServicing {
    /** `profiles.display_name` of the signed-in user, trimmed; "" when unset or unreadable (logged). */
    suspend fun displayName(): String

    /** `home_wraps(p_tz)`: the months holding a pebble, newest first. A month this build cannot read is dropped. */
    suspend fun wraps(zone: ZoneId): List<HomeWrapMonth>

    /**
     * `reveal_wrap(p_month, p_tz)`: unfolds [month] and answers when the server recorded it. A null
     * `revealed_at` (the server recorded nothing) throws, which `toDataError()` reads as `Unknown`.
     */
    suspend fun reveal(
        month: YearMonth,
        zone: ZoneId,
    ): Instant

    /** The published About Pebbles readings, by `sort_order`. A row this build cannot read is dropped. */
    suspend fun guides(): List<Guide>
}

@Singleton
class HomeService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : HomeServicing {
        override suspend fun displayName(): String =
            runCatchingCancellable {
                // RLS returns the caller's own row, as the profile read relies on.
                supabase.client
                    .from("profiles")
                    .select(Columns.raw("display_name"))
                    .decodeSingle<DisplayNameRow>()
                    .displayName
                    ?.trim()
                    .orEmpty()
            }.getOrElse {
                Log.w(TAG, "display name read failed; greeting without a name", it)
                ""
            }

        override suspend fun wraps(zone: ZoneId): List<HomeWrapMonth> {
            val body =
                supabase.client.postgrest
                    .rpc("home_wraps", buildJsonObject { put("p_tz", zone.id) })
                    .data
            return decodeHomeWraps(body) { index, cause ->
                Log.w(TAG, "home_wraps: dropped month $index the client could not decode", cause)
            }
        }

        override suspend fun reveal(
            month: YearMonth,
            zone: ZoneId,
        ): Instant {
            val body =
                supabase.client.postgrest
                    .rpc(
                        "reveal_wrap",
                        buildJsonObject {
                            put("p_month", month.atDay(1).toString())
                            put("p_tz", zone.id)
                        },
                    ).data
            return decodeRevealedAt(body) ?: throw IllegalStateException("reveal_wrap recorded no revealed_at for $month")
        }

        override suspend fun guides(): List<Guide> {
            val body =
                supabase.client
                    .from("guides")
                    .select {
                        filter { eq("published", true) }
                        order("sort_order", Order.ASCENDING)
                    }.data
            return decodeGuides(body) { index, cause ->
                Log.e(TAG, "skipped guide row $index", cause)
            }
        }

        @Serializable
        private data class DisplayNameRow(
            @SerialName("display_name")
            val displayName: String? = null,
        )
    }
