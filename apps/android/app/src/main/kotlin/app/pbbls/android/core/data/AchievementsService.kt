package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementsJson
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CeremonyRowDto
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.ceremonyPullOf
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import app.pbbls.android.di.ApplicationScope
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
 * One of the caller's `achievement_unlocks` rows. The time stays a string (Postgres sends
 * microseconds): only the id is used, so no timestamp precision can fail the row.
 */
@Serializable
private data class UnlockRow(
    @SerialName("achievement_id")
    val achievementId: String,
    @SerialName("unlocked_at")
    val unlockedAt: String? = null,
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
 * the raw result, and its two wrappers below are the whole check contract. The
 * page's calls (#1307) follow them: the read, its device copy, the seen marks
 * and the ceremony pull.
 */
interface AchievementsServicing {
    suspend fun loadCatalog(): List<AchievementRecord>

    /** The ids of the caller's unlocked achievements (`achievement_unlocks`, owner-scoped by RLS). Throws. */
    suspend fun loadUnlocks(): Set<String>

    /**
     * `claim_blue_pebble()` (#1254, #1383): the sixth row of Home's first five, granted only through
     * this call once the five firsts are unlocked. An EMPTY list means not granted (already claimed,
     * or the catalog row retired), never success. A grant plays the unlock moment, as a mutation's
     * check does. `not_ready` throws (`DataError.Conflict("not_ready")` through `toDataError()`).
     */
    suspend fun claimBluePebble(): List<AchievementCheckResult>

    suspend fun checkIgnoringFailure()

    fun fireCheck()

    /**
     * A mutation-path [fireCheck] has left and not finished. A screen-open grant run now could take
     * its unlocks and leave them uncelebrated, so the Profile shelf skips its grant meanwhile.
     */
    val isFiringCheck: Boolean

    /**
     * `get_achievements(p_tz)` (#1307), decoded, and kept on the device for the signed-in user
     * once it has decoded. Throws.
     */
    suspend fun page(zone: ZoneId): AchievementsPage

    /** The device's last copy for the signed-in user, or null: display only, never instead of [page]. */
    suspend fun cachedPage(zone: ZoneId): AchievementsPage?

    /**
     * How old the signed-in user's page is (D18): when the device's copy was kept, or the last
     * read in this process landed, whichever is later; null with neither, or once a check that
     * unlocked something or a pulled ceremony has made the page stale, until the next read.
     */
    suspend fun keptAt(zone: ZoneId): Instant?

    /** `mark_achievements_seen(p_card)`, fire-and-forget: it outlives the sheet that sent it. */
    fun markSeen(card: AchievementCard)

    /**
     * `pull_ceremony(p_stack, p_tz)`. A refusal throws `PostgrestRestException`, and
     * [toDataError] reads it: `not_ready` (P0001) and `invalid_stack`/`invalid_tz` (22023, a 400)
     * as `DataError.Conflict(code)`, `not_authenticated` (42501) as `DataError.Unauthorized`; a
     * transport failure is an `IOException`, `DataError.Network`.
     */
    suspend fun pullCeremony(
        stack: CompletionStack,
        zone: ZoneId,
    ): CeremonyPull
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
 *
 * The page's device copy reuses the wraps' [WrapDiskCaching] (#1207 §7.1) because its contract
 * is exactly this one: one file per page under the signed-in user and the zone, never backed up,
 * wiped by `RootViewModel` whenever the device resolves signed out, and a format bump that drops
 * old files. The key [CACHE_PAGE] never collides with a wrap's `strip`, `week-…` or `month-…`.
 */
@Singleton
class AchievementsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val notify: AchievementNotificationService,
        @ApplicationScope private val scope: CoroutineScope,
        private val disk: WrapDiskCaching,
    ) : AchievementsServicing {
        /** When each user's page last landed in this process, by zone: the shelf's fifteen minutes (D18). */
        private val lastRead = ConcurrentHashMap<Pair<String, String>, Instant>()

        /**
         * Reads overlap (the page, its resume, a pull's re-read, the shelf): each takes a number when
         * it leaves, and only one newer than the last kept is kept, so a slow old answer never
         * overwrites a newer copy.
         */
        private val readsStarted = AtomicLong(0)
        private var lastKeptRead = 0L
        private val keepLock = Mutex()

        /**
         * Bumped when the kept page no longer says what the server would (a check unlocked
         * something, a ceremony was pulled): the next shelf open reads rather than wait out its
         * fifteen minutes. The copy itself stays, for display. Only a read that left after the bump
         * makes the page fresh again; one already in flight may not have seen the change.
         */
        private val staleGeneration = AtomicLong(0)

        @Volatile
        private var freshGeneration = 0L

        /**
         * Full catalog, inactive rows included: the unlock moment and the Profile
         * shelf title badges from it by slug.
         */
        override suspend fun loadCatalog(): List<AchievementRecord> =
            supabase.client
                .from(TABLE)
                .select(Columns.raw(CATALOG_COLUMNS)) {
                    order("sort_order", Order.ASCENDING)
                }.decodeList()

        override suspend fun loadUnlocks(): Set<String> =
            supabase.client
                .from(UNLOCKS_TABLE)
                .select(Columns.raw("achievement_id, unlocked_at"))
                .decodeList<UnlockRow>()
                .map { it.achievementId }
                .toSet()

        override suspend fun claimBluePebble(): List<AchievementCheckResult> {
            val results =
                supabase.client.postgrest
                    .rpc("claim_blue_pebble")
                    .decodeList<AchievementCheckResult>()
            if (results.isNotEmpty()) {
                markPageStale()
                notify.present(unlockCards(results))
            }
            return results
        }

        /**
         * The moment's cards for [results], in the order the ladder reads. A catalog that fails to
         * load still celebrates, headlined by the slug (iOS `presentUnlockMoment`).
         */
        private suspend fun unlockCards(results: List<AchievementCheckResult>): List<AchievementMomentCard> {
            val bySlug =
                runCatchingCancellable { loadCatalog().associateBy { it.slug } }
                    .getOrElse {
                        Log.w(TAG, "catalog fetch for the unlock moment failed", it)
                        emptyMap()
                    }
            return results
                .sortedBy { bySlug[it.slug]?.sortOrder ?: Int.MAX_VALUE }
                .map { AchievementMomentCard(slug = it.slug, record = bySlug[it.slug], karmaGranted = it.karmaGranted) }
        }

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
         * error — the page renders whatever `get_achievements` then returns.
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
        private val checksInFlight = AtomicInteger(0)

        override val isFiringCheck: Boolean get() = checksInFlight.get() > 0

        override fun fireCheck() {
            // Counted before the launch, so a grant asked right after this call already sees it.
            checksInFlight.incrementAndGet()
            scope.launch {
                try {
                    val results = check()
                    if (results.isEmpty()) return@launch
                    markPageStale()
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
                } finally {
                    checksInFlight.decrementAndGet()
                }
            }
        }

        override suspend fun page(zone: ZoneId): AchievementsPage {
            // The body is the caller's, as RLS saw them when the request left: capture who that
            // was before the call, so a session that swaps while it is in flight never files one
            // user's page under the next one.
            val owner = supabase.session?.user?.id
            val read = readsStarted.incrementAndGet()
            val generation = staleGeneration.get()
            val body =
                supabase.client.postgrest
                    .rpc("get_achievements", buildJsonObject { put("p_tz", zone.id) })
                    .data
            val page = decodeAchievementsPage(body)
            // Kept only once it has decoded, so the device never holds a body it cannot read.
            if (owner != null && supabase.session?.user?.id == owner) {
                keepLock.withLock {
                    if (read > lastKeptRead) {
                        lastKeptRead = read
                        lastRead[owner to zone.id] = Instant.now()
                        disk.write(owner, zone, CACHE_PAGE, body)
                    } else {
                        Log.i(TAG, "an older get_achievements landed after a newer one was kept; not kept")
                    }
                }
                if (generation == staleGeneration.get()) freshGeneration = maxOf(freshGeneration, generation)
            } else if (owner != null) {
                Log.w(TAG, "the session changed during get_achievements; its page is not kept")
            }
            return page
        }

        override suspend fun cachedPage(zone: ZoneId): AchievementsPage? {
            val owner = supabase.session?.user?.id ?: return null
            val body = disk.read(owner, zone, CACHE_PAGE) ?: return null
            return try {
                decodeAchievementsPage(body)
            } catch (e: IllegalArgumentException) {
                // SerializationException included: it is an IllegalArgumentException.
                Log.w(TAG, "kept achievements page no longer decodes, read as a miss", e)
                null
            }
        }

        override suspend fun keptAt(zone: ZoneId): Instant? {
            if (freshGeneration < staleGeneration.get()) return null
            val owner = supabase.session?.user?.id ?: return null
            return listOfNotNull(disk.keptAt(owner, zone, CACHE_PAGE), lastRead[owner to zone.id]).maxOrNull()
        }

        override fun markSeen(card: AchievementCard) {
            // @ApplicationScope has no handler: catch inside (DispatchersModule's KDoc).
            scope.launch {
                runCatchingCancellable {
                    supabase.client.postgrest.rpc("mark_achievements_seen", buildJsonObject { put("p_card", card.slug) })
                }.onFailure { Log.w(TAG, "mark_achievements_seen(${card.slug}) failed; the marks stay until the next look", it) }
            }
        }

        override suspend fun pullCeremony(
            stack: CompletionStack,
            zone: ZoneId,
        ): CeremonyPull {
            val body =
                supabase.client.postgrest
                    .rpc(
                        "pull_ceremony",
                        buildJsonObject {
                            put("p_stack", stack.slug)
                            put("p_tz", zone.id)
                        },
                    ).data
            val pull = ceremonyPullOf(stack, AchievementsJson.decodeFromString<List<CeremonyRowDto>>(body))
            // A pull that landed changed the page (and may have paid karma): the shelf reads again.
            if (pull is CeremonyPull.Pulled) markPageStale()
            return pull
        }

        private fun markPageStale() {
            staleGeneration.incrementAndGet()
            lastRead.clear()
        }

        private companion object {
            const val TABLE = "achievements"
            const val UNLOCKS_TABLE = "achievement_unlocks"
            const val CACHE_PAGE = "achievements-page"
            const val CATALOG_COLUMNS =
                "id, slug, family, threshold, emotion_id, domain_id, sort_order, " +
                    "glyph_id, karma_reward, is_active, title_en, title_fr, description_en, description_fr"
        }
    }
