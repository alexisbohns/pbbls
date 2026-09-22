package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.OffsetDateTimeSerializer
import app.pbbls.android.di.ApplicationScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "achievements"

/**
 * One `achievements` catalog row (M48). Public-read reference data; the copy
 * columns are nullable admin overrides — display copy resolves through
 * `AchievementCopy` from them or from the family-keyed string resources.
 */
@Serializable
data class AchievementRecord(
    val id: String,
    val slug: String,
    val family: String,
    val threshold: Int? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    @SerialName("domain_id")
    val domainId: String? = null,
    @SerialName("sort_order")
    val sortOrder: Int,
    @SerialName("glyph_id")
    val glyphId: String? = null,
    @SerialName("karma_reward")
    val karmaReward: Int,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("title_en")
    val titleEn: String? = null,
    @SerialName("title_fr")
    val titleFr: String? = null,
    @SerialName("description_en")
    val descriptionEn: String? = null,
    @SerialName("description_fr")
    val descriptionFr: String? = null,
)

/**
 * One of the caller's `achievement_unlocks` rows (owner-scoped by RLS).
 * `unlocked_at` stays an [OffsetDateTime] through [OffsetDateTimeSerializer] —
 * PostgREST emits `+00:00` offsets that `Instant.parse` rejects (#651).
 */
@Serializable
data class AchievementUnlockRecord(
    @SerialName("achievement_id")
    val achievementId: String,
    @SerialName("unlocked_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val unlockedAt: OffsetDateTime,
)

/**
 * One newly unlocked badge as `check_achievements()` reports it: the karma is
 * what the server actually emitted at unlock time, never the catalog's
 * current value.
 */
@Serializable
data class AchievementCheckResult(
    val slug: String,
    @SerialName("karma_granted")
    val karmaGranted: Int,
)

/**
 * The achievements seam (#849).
 *
 * Extracted because `AchievementsViewModel` has a test, which is the standing
 * bar for an interface here (`apps/android/CLAUDE.md`) — not because the layer
 * wanted one on principle. [AchievementsService]'s constructor reaches
 * `SupabaseService`, so without this a JVM test of the screen's load and error
 * paths would need a live project.
 *
 * [check] stays off it deliberately: no caller outside the implementation uses
 * the raw result, and the two wrappers below are the whole public contract.
 */
interface AchievementsServicing {
    suspend fun loadCatalog(): List<AchievementRecord>

    suspend fun loadUnlocks(): List<AchievementUnlockRecord>

    suspend fun checkIgnoringFailure()

    fun fireCheck()
}

/**
 * Achievements data access + the fire-and-forget evaluation call (M48).
 *
 * `check_achievements()` is the design's single evaluation path: idempotent,
 * client-called after count-changing mutations and on screen open — the
 * screen-open call is the retroactive grant, so a failed fire self-heals on
 * the next call and must never block or fail the mutation that triggered it.
 *
 * The [scope] is constructor-injectable so `fireCheck`'s launch is
 * unit-testable on a virtual clock, mirroring [AchievementNotificationService].
 */
@Singleton
class AchievementsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val notify: AchievementNotificationService,
        @ApplicationScope private val scope: CoroutineScope,
    ) : AchievementsServicing {
        /**
         * Full catalog, inactive rows included — the screen filters (an inactive
         * badge stays visible once unlocked, hidden while locked).
         */
        override suspend fun loadCatalog(): List<AchievementRecord> =
            supabase.client
                .from(TABLE)
                .select(Columns.raw(CATALOG_COLUMNS)) {
                    order("sort_order", Order.ASCENDING)
                }.decodeList()

        /** The caller's unlocks; RLS scopes to `auth.uid()`. */
        override suspend fun loadUnlocks(): List<AchievementUnlockRecord> =
            supabase.client
                .from("achievement_unlocks")
                .select(Columns.raw("achievement_id, unlocked_at"))
                .decodeList()

        /**
         * Runs the evaluation. SETOF-returning RPC, so PostgREST yields an array
         * (empty = nothing newly unlocked).
         */
        suspend fun check(): List<AchievementCheckResult> =
            supabase.client.postgrest
                .rpc("check_achievements")
                .decodeList()

        /**
         * Screen-open variant: the retroactive grant must never surface as an
         * error — the grid renders whatever `loadUnlocks()` then returns.
         */
        override suspend fun checkIgnoringFailure() {
            runCatchingCancellable { check() }
                .onFailure {
                    Log.w(TAG, "achievement check on screen open failed (self-heals on next call)", it)
                }
        }

        /**
         * Mutation-path variant: fire-and-forget from a success handler. Never
         * throws, never blocks the caller. New unlocks open the chained moment
         * (D13); karma notifies stay untouched — each card carries the badge's own
         * "+N karma" line, never the pebble's.
         *
         * Cards carry their catalog row so the composition can resolve localized
         * copy; the one extra catalog read per actual unlock (rare) keeps i18n out
         * of six call sites.
         */
        override fun fireCheck() {
            scope.launch {
                try {
                    val results = check()
                    if (results.isEmpty()) return@launch
                    val bySlug =
                        try {
                            loadCatalog().associateBy { it.slug }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "catalog fetch for the unlock moment failed", e)
                            emptyMap()
                        }
                    val cards =
                        results
                            // Chain in the order the ladder reads, so a multi-tier
                            // unlock walks up rather than arriving shuffled. A slug
                            // the catalog does not know (a check racing a catalog
                            // change) still celebrates, and sorts last.
                            .sortedBy { bySlug[it.slug]?.sortOrder ?: Int.MAX_VALUE }
                            .map { result ->
                                AchievementMomentCard(
                                    slug = result.slug,
                                    record = bySlug[result.slug],
                                    karmaGranted = result.karmaGranted,
                                )
                            }
                    notify.present(cards)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "achievement check failed (self-heals on next call)", e)
                }
            }
        }

        private companion object {
            const val TABLE = "achievements"
            const val CATALOG_COLUMNS =
                "id, slug, family, threshold, emotion_id, domain_id, sort_order, " +
                    "glyph_id, karma_reward, is_active, title_en, title_fr, description_en, description_fr"
        }
    }
