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
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.core.data.ProfileServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.core.model.achievements.AchievementsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "profile"

/** How old the achievements page may be before a visit to You reads it again (D18, iOS's fifteen minutes). */
private val SHELF_MAX_AGE: Duration = Duration.ofMinutes(15)

/** What the Profile screen can be showing (#849). */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : ProfileUiState

    data class Content(
        val profile: ProfileRow?,
        val glyphStrokes: List<GlyphStroke>?,
        val collections: List<Collection>,
        /**
         * Distinct from an empty list: the carousel shows a skeleton until the
         * fetch settles, and an empty result is a real "no collections yet"
         * tile. Collapsing the two would flash the empty state on every open.
         */
        val collectionsLoaded: Boolean,
        val karma: Int?,
        val pebbles: Int?,
        val daysPracticed: Int?,
        val assiduity: List<Boolean>?,
        val ripple: RippleSummary?,
        /** The account's own identity, for the Settings cover. */
        val email: String?,
        /** SSO brand labels, rendered verbatim and never localized. */
        val providers: List<String>,
        /** The shelf's badges as `get_achievements` serves them (D18): the server's picks, in its order. */
        val shelf: List<AchievementRecord>,
        /** The served count of badges earned; null when none is served, and the shelf invites instead. */
        val shelfCount: Int?,
    ) : ProfileUiState
}

/**
 * State holder for the Profile screen (#849).
 *
 * The load has three parts with deliberately different failure behaviour, which
 * is why it is not one `runCatching`:
 *
 * - **the profile row** is the screen — its failure is the error state (D13,
 *   Android's named deviation from iOS's silent empty banner);
 * - **the glyph strokes** are decoration hanging off that row, so a failure
 *   logs and the banner renders without a glyph;
 * - **the collections** are their own card, so a failure leaves that card
 *   empty rather than taking the screen down.
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
    ) : ViewModel() {
        private var profile: ProfileRow? = null
        private var glyphStrokes: List<GlyphStroke>? = null
        private var collections: List<Collection> = emptyList()
        private var collectionsLoaded = false
        private var shelfPage: AchievementsPage? = null
        private var catalogBySlug: Map<String, AchievementRecord> = emptyMap()

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
                    listOf(stats.karma, stats.pebbles, stats.daysPracticed, stats.assiduity, stats.ripple)
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
        }

        private suspend fun fetch() {
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

            runCatchingCancellable { profileService.loadCollections() }
                .fold(
                    onSuccess = { collections = it },
                    onFailure = { Log.e(TAG, "collections fetch failed", it) },
                )
            collectionsLoaded = true
            publish()

            loadShelf()
        }

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
                achievementsService.checkIgnoringFailure()
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
                            collections = collections,
                            collectionsLoaded = collectionsLoaded,
                            karma = stats.karma,
                            pebbles = stats.pebbles,
                            daysPracticed = stats.daysPracticed,
                            assiduity = stats.assiduity,
                            ripple = stats.ripple,
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
                        )
                }
        }
    }
