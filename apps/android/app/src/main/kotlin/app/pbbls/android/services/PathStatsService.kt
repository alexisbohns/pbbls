package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.profile.models.KarmaSummary
import app.pbbls.android.features.profile.models.ProfileEngagement
import app.pbbls.android.features.shared.ripples.RippleSummary
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

/**
 * Shared wrapper around `v_karma_summary`, `v_ripple`, and
 * `get_profile_engagement(p_tz)` — ports iOS `PathStatsService.swift`.
 * PathScreen (bottom bar) and the Profile screen read the same instance so a
 * reload from one screen is visible to the other. The three sources load in
 * parallel with per-source error isolation: one failing fetch logs and leaves
 * its slice null instead of blanking the others.
 *
 * Display-only by contract: karma-flash amounts come exclusively from the
 * edge-function `karma_delta` (M39 D10) — never from this service.
 */
class PathStatsService(
    private val supabase: SupabaseService,
) {
    var karma: Int? by mutableStateOf(null)
        private set

    var pebbles: Int? by mutableStateOf(null)
        private set

    var ripple: RippleSummary? by mutableStateOf(null)
        private set

    var daysPracticed: Int? by mutableStateOf(null)
        private set

    var assiduity: List<Boolean>? by mutableStateOf(null)
        private set

    var hasLoaded: Boolean by mutableStateOf(false)
        private set

    private var isLoading = false

    /**
     * Idempotent. Returns immediately if already loaded or currently loading,
     * so it is safe to call from every screen's LaunchedEffect.
     */
    suspend fun load() {
        if (hasLoaded || isLoading) return
        performLoad()
    }

    /**
     * Forces a network reload, bypassing the [hasLoaded] cache. Still guards
     * against concurrent calls so spam-tapping cannot fan out parallel queries.
     */
    suspend fun refresh() {
        if (isLoading) return
        performLoad()
    }

    private suspend fun performLoad() {
        isLoading = true
        try {
            coroutineScope {
                val karmaDeferred =
                    async {
                        runCatching {
                            supabase.client
                                .from("v_karma_summary")
                                .select(Columns.raw("total_karma, pebbles_count"))
                                .decodeSingle<KarmaSummary>()
                        }
                    }
                val rippleDeferred =
                    async {
                        runCatching {
                            supabase.client
                                .from("v_ripple")
                                .select(Columns.raw("ripple_level, pebbles_28d, active_today"))
                                .decodeSingle<RippleSummary>()
                        }
                    }
                val engagementDeferred =
                    async {
                        runCatching {
                            supabase.client.postgrest
                                .rpc(
                                    "get_profile_engagement",
                                    buildJsonObject { put("p_tz", ZoneId.systemDefault().id) },
                                ).decodeList<ProfileEngagement>()
                        }
                    }

                karmaDeferred.await().fold(
                    onSuccess = {
                        karma = it.totalKarma
                        pebbles = it.pebblesCount
                    },
                    onFailure = { Log.e(TAG, "karma fetch failed", it) },
                )
                rippleDeferred.await().fold(
                    onSuccess = { ripple = it },
                    onFailure = { Log.e(TAG, "ripple fetch failed", it) },
                )
                engagementDeferred.await().fold(
                    onSuccess = { rows ->
                        rows.firstOrNull()?.let {
                            daysPracticed = it.daysPracticed
                            assiduity = it.assiduity
                        }
                    },
                    onFailure = { Log.e(TAG, "engagement fetch failed", it) },
                )
            }
        } finally {
            isLoading = false
        }
        hasLoaded = true
    }

    companion object {
        private const val TAG = "path-stats"
    }
}

/** CompositionLocal for [PathStatsService] — see [LocalSupabaseService] (D4). */
val LocalPathStatsService =
    staticCompositionLocalOf<PathStatsService> {
        error("LocalPathStatsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
