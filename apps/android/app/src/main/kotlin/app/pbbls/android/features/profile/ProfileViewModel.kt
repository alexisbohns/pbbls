package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "profile"

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
    ) : ProfileUiState
}

/** Which of Profile's two covers is up. */
data class ProfileCovers(
    val isPresentingSettings: Boolean = false,
    val isPresentingCreateCollection: Boolean = false,
)

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
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private var profile: ProfileRow? = null
        private var glyphStrokes: List<GlyphStroke>? = null
        private var collections: List<Collection> = emptyList()
        private var collectionsLoaded = false
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(ProfileCovers())
        val covers: StateFlow<ProfileCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null

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
                        )
                }
        }

        // MARK: - Covers

        fun openSettings() = _covers.update { it.copy(isPresentingSettings = true) }

        fun closeSettings() = _covers.update { it.copy(isPresentingSettings = false) }

        fun openCreateCollection() = _covers.update { it.copy(isPresentingCreateCollection = true) }

        fun closeCreateCollection() = _covers.update { it.copy(isPresentingCreateCollection = false) }

        fun onCollectionCreated() {
            _covers.update { it.copy(isPresentingCreateCollection = false) }
            refresh()
            // The composer's collection picker reads the shared cache, so a new
            // collection has to reach it too.
            viewModelScope.launch { refs.refreshCollections() }
        }

        /**
         * Settings saved: fold the new values into the row already on screen
         * rather than refetching, so closing the cover does not flash a spinner
         * over content that is already correct.
         */
        fun onSettingsSaved(
            displayName: String,
            glyph: Glyph?,
            handle: String?,
            isPublic: Boolean,
        ) {
            profile =
                profile?.copy(
                    displayName = displayName,
                    glyphId = glyph?.id ?: profile?.glyphId,
                    handle = handle,
                    publicProfile = isPublic,
                )
            glyph?.strokes?.let { glyphStrokes = it }
            _covers.update { it.copy(isPresentingSettings = false) }
            publish()
        }
    }
