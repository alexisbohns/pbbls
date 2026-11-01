package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.LogsServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.core.data.ProfileServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.core.model.achievements.AchievementsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import app.pbbls.android.core.model.Log as LabLog

private const val TAG = "profile"

/** How old the achievements page may be before a visit to You reads it again (D18, iOS's fifteen minutes). */
private val SHELF_MAX_AGE: Duration = Duration.ofMinutes(15)

/** You shows the latest announcement and the three latest shipped features (#1383, spec D16). */
private const val LAB_ANNOUNCEMENTS = 1
private const val LAB_ENTRIES = 3

/** What the Profile screen can be showing (#849). */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : ProfileUiState

    data class Content(
        val profile: ProfileRow?,
        val glyphStrokes: List<GlyphStroke>?,
        val karma: Int?,
        val pebbles: Int?,
        val daysPracticed: Int?,
        val ripple: RippleSummary?,
        /** The caller's carved glyphs (#1386); null until served, and the Glyphs line reads without a number. */
        val glyphsCarved: Int?,
        /** The glyphs the caller owns from others (#1386); null until served. */
        val glyphsOwned: Int?,
        /** The account's own identity, for the Settings cover. */
        val email: String?,
        /** SSO brand labels, rendered verbatim and never localized. */
        val providers: List<String>,
        /** The shelf's badges as `get_achievements` serves them (D18): the server's picks, in its order. */
        val shelf: List<AchievementRecord>,
        /** The served count of badges earned; null when none is served, and the shelf invites instead. */
        val shelfCount: Int?,
        /** The latest published announcement; null when none is published or its read failed (D16). */
        val announcement: LabLog? = null,
        /** [announcement]'s public cover, or null to draw the split-stone band instead. */
        val announcementCoverUrl: String? = null,
        /** The three latest shipped features, newest first; empty when the read failed (D16). */
        val labEntries: List<LabLog> = emptyList(),
    ) : ProfileUiState
}

/**
 * State holder for the Profile screen (#849).
 *
 * The load has two parts with deliberately different failure behaviour, which
 * is why it is not one `runCatching`:
 *
 * - **the profile row** is the screen — its failure is the error state (D13,
 *   Android's named deviation from iOS's silent empty banner);
 * - **the glyph strokes** are decoration hanging off that row, so a failure
 *   logs and the avatar renders the dotted pebble instead.
 *
 * - **the Lab's two feeds** (#1383, spec D16), the latest announcement and the three latest
 *   shipped features, are read in parallel with the rest and fail alone: a failed feed logs and
 *   hides its block (or, on a reload, keeps what is shown).
 *
 * Collections are no longer read here: You lost its collections card (#1383,
 * spec D14), and the Collections tab is their home.
 *
 * Stats come from the shared [PathStatsServicing] singleton, observed through
 * `snapshotFlow` for the same reason the Path does it: Settings and the glyph
 * picker write to that instance too.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val profileService: ProfileServicing,
        private val stats: PathStatsServicing,
        private val supabase: SupabaseServicing,
        private val achievementsService: AchievementsServicing,
        private val logsService: LogsServicing,
    ) : ViewModel() {
        private var profile: ProfileRow? = null
        private var glyphStrokes: List<GlyphStroke>? = null
        private var shelfPage: AchievementsPage? = null
        private var catalogBySlug: Map<String, AchievementRecord> = emptyMap()
        private var announcement: LabLog? = null
        private var labEntries: List<LabLog> = emptyList()

        /** The reader's zone, read at each load: the shelf shares the page's copy, kept by zone. */
        @VisibleForTesting
        internal var zone: () -> ZoneId = { ZoneId.systemDefault() }

        @VisibleForTesting
        internal var now: () -> Instant = { Instant.now() }
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            load()
            viewModelScope.launch { stats.load() }
            viewModelScope.launch {
                snapshotFlow {
                    listOf(stats.karma, stats.pebbles, stats.daysPracticed, stats.ripple, stats.glyphsCarved, stats.glyphsOwned)
                }.collect { publish() }
            }
        }

        fun retry() = load()

        /**
         * Profile came back to the foreground.
         *
         * [refresh] has carried the KDoc "for returning from a pushed screen"
         * since #893 and was never called for it: the ViewModel is scoped to the
         * `NavBackStackEntry`, which survives the trip to Souls, Collections,
         * Glyphs, Connections, Lab or Achievements. So renaming a collection in
         * its own list left Profile's carousel showing the old name, and the
         * stats card stale after anything that changes counts.
         *
         * Since #852, this is also what refreshes after Settings and the
         * create-collection form: both are entries now, with no callback back
         * into this ViewModel, so their old `onXSaved` hooks are gone and this
         * generic resume is what picks up the change instead.
         *
         * The first resume is skipped (`init` has already loaded). Deferred from
         * PR #896.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) refresh()
        }

        private fun load() {
            loadJob?.cancel()
            _uiState.value = ProfileUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        /**
         * Reload without the spinner — for returning from a pushed screen, where
         * the content is already on display.
         */
        private fun refresh() {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch() }
            // The counters and the glyph counts live in the shared stats singleton, which only
            // the Path refreshes; a glyph carved or bought on the way out must show on return.
            viewModelScope.launch { stats.refresh() }
        }

        private suspend fun fetch() =
            coroutineScope {
                // The Lab is not the screen: it runs beside the profile and publishes when it lands.
                val lab = async { loadLab() }
                fetchProfile()
                lab.await()
            }

        private suspend fun fetchProfile() {
            runCatchingCancellable { profileService.loadProfile() }
                .fold(
                    onSuccess = {
                        profile = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "profile fetch failed", it)
                        if (!isLoaded) hasFailed = true
                    },
                )
            publish()

            profile?.glyphId?.let { id ->
                runCatchingCancellable { profileService.loadGlyphStrokes(id) }
                    .fold(
                        onSuccess = { glyphStrokes = it },
                        // Decoration, not the screen: the banner renders without it.
                        onFailure = { Log.e(TAG, "glyph fetch failed", it) },
                    )
                publish()
            }

            loadShelf()
        }

        /**
         * The Lab's two feeds, in parallel, each failing alone (D16): a failure logs and leaves
         * that block as it was, hidden on a first load.
         */
        private suspend fun loadLab() =
            coroutineScope {
                val posts = async { labFeed("announcements") { logsService.announcements(LAB_ANNOUNCEMENTS) } }
                val shipped = async { labFeed("changelog") { logsService.changelog(LAB_ENTRIES) } }
                posts.await()?.let { announcement = it.firstOrNull() }
                shipped.await()?.let { labEntries = it.take(LAB_ENTRIES) }
                publish()
            }

        private suspend fun <T> labFeed(
            label: String,
            read: suspend () -> T,
        ): T? =
            runCatchingCancellable { read() }
                .onFailure { Log.e(TAG, "lab $label read failed; its block keeps what is shown", it) }
                .getOrNull()

        /**
         * The shelf (D18, iOS `loadIfStale`): the achievements page's kept copy at once, then a read
         * only when that copy, or this process's last read, is fifteen minutes old or more, or a
         * check or a ceremony has made it stale. A read runs `check_achievements()` first, as the
         * page does (the retroactive grant, which never celebrates). A failure keeps what is shown,
         * and with nothing the shelf just invites.
         */
        private suspend fun loadShelf() {
            val zone = zone()
            // The kept copy every time, not only the first: the page may have read a newer one
            // since, and the shelf then shows it without asking the server.
            runCatchingCancellable { achievementsService.cachedPage(zone) }
                .onFailure { Log.w(TAG, "the kept achievements page could not be read", it) }
                .getOrNull()
                ?.let {
                    shelfPage = it
                    publish()
                }
            val keptAt = runCatchingCancellable { achievementsService.keptAt(zone) }.getOrNull()
            val age = keptAt?.let { Duration.between(it, now()) }
            val fresh = shelfPage != null && age != null && !age.isNegative && age < SHELF_MAX_AGE
            if (!fresh) {
                // Not while a mutation's own check is in flight: this one could take its unlocks
                // and leave them uncelebrated. That check grants them anyway.
                if (achievementsService.isFiringCheck) {
                    Log.i(TAG, "a mutation's achievement check is in flight; the shelf reads without granting")
                } else {
                    achievementsService.checkIgnoringFailure()
                }
                runCatchingCancellable { achievementsService.page(zone) }
                    .fold(
                        onSuccess = { shelfPage = it },
                        onFailure = { Log.e(TAG, "achievements shelf read failed; keeping what is shown", it) },
                    )
                publish()
            }
            // The catalog once, for titles and icons; the served rows stand in until it lands.
            if (catalogBySlug.isEmpty() && shelfPage?.shelf?.isNotEmpty() == true) {
                runCatchingCancellable { achievementsService.loadCatalog() }
                    .fold(
                        onSuccess = { rows -> catalogBySlug = rows.distinctBy { it.slug }.associateBy { it.slug } },
                        onFailure = { Log.e(TAG, "achievements catalog read failed; the shelf shows the served rows", it) },
                    )
                publish()
            }
        }

        private fun publish() {
            _uiState.value =
                when {
                    hasFailed -> ProfileUiState.Error(R.string.profile_load_error)
                    !isLoaded -> ProfileUiState.Loading
                    else ->
                        ProfileUiState.Content(
                            profile = profile,
                            glyphStrokes = glyphStrokes,
                            karma = stats.karma,
                            pebbles = stats.pebbles,
                            daysPracticed = stats.daysPracticed,
                            ripple = stats.ripple,
                            glyphsCarved = stats.glyphsCarved,
                            glyphsOwned = stats.glyphsOwned,
                            email = supabase.session?.user?.email,
                            providers =
                                linkedProviders(
                                    supabase.session
                                        ?.user
                                        ?.identities
                                        ?.map { it.provider },
                                ),
                            shelf = ProfileShelf.badges(shelfPage, catalogBySlug),
                            shelfCount = ProfileShelf.count(shelfPage),
                            announcement = announcement,
                            announcementCoverUrl = announcement?.let(logsService::coverImageUrl),
                            labEntries = labEntries,
                        )
                }
        }
    }
