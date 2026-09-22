package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.SupabaseServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "profile"

/** How many recent badges the shelf shows before "view all" takes over. */
private const val ACHIEVEMENTS_SHELF_SIZE = 6

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
        /** Most recently unlocked badges, newest first — the shelf's row. */
        val recentAchievements: List<AchievementRecord>,
        val unlockedAchievementCount: Int,
        /**
         * Distinct from an empty list for the same reason [collectionsLoaded]
         * is: "nothing unlocked yet" and "still loading" render differently.
         */
        val achievementsLoaded: Boolean,
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
        private var recentAchievements: List<AchievementRecord> = emptyList()
        private var unlockedAchievementCount = 0
        private var achievementsLoaded = false
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

            // Reads only — the shelf never fires an evaluation. The grid's
            // screen-open call is the retroactive grant; doing it here too
            // would double the work on every profile visit. A failure is not
            // worth taking the screen down for: the card still navigates, and
            // the grid surfaces real failures.
            runCatchingCancellable {
                val catalog = achievementsService.loadCatalog().associateBy { it.id }
                achievementsService
                    .loadUnlocks()
                    .sortedByDescending { it.unlockedAt }
                    .mapNotNull { catalog[it.achievementId] }
            }.fold(
                onSuccess = {
                    unlockedAchievementCount = it.size
                    recentAchievements = it.take(ACHIEVEMENTS_SHELF_SIZE)
                },
                onFailure = { Log.e(TAG, "achievements shelf fetch failed", it) },
            )
            achievementsLoaded = true
            publish()
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
                            recentAchievements = recentAchievements,
                            unlockedAchievementCount = unlockedAchievementCount,
                            achievementsLoaded = achievementsLoaded,
                        )
                }
        }
    }
