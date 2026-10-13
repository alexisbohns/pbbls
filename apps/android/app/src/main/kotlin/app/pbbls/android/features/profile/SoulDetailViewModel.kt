package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SoulsServicing
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "soul-detail"

/**
 * One soul and the moments whose pebble is tagged with it, newest first (#849, R9).
 *
 * The header and the list load together and fail together — a soul without its
 * pebbles is not a screen this surface can render — so they are one [Content],
 * not two independently-nullable fields with a shared spinner flag.
 */
sealed interface SoulDetailUiState {
    data object Loading : SoulDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : SoulDetailUiState

    data class Content(
        val soul: SoulWithGlyph,
        val moments: List<PathMoment>,
    ) : SoulDetailUiState
}

/** The delete dialogs. */
data class SoulDetailCovers(
    val pendingDeletion: PathMoment? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the pushed soul detail (#849).
 *
 * Two things it fixes beyond moving code:
 *
 * - **Rotation.** The load hung off `LaunchedEffect(soulId, loadKey)` over nine
 *   `remember(soulId)` values, so turning the phone re-fetched the soul and its
 *   pebbles and flashed the spinner over a list the user was reading. [start]
 *   is guarded on the id, so a re-run after a configuration change is free.
 * - **The pebble delete.** It ran in `rememberCoroutineScope`; leaving between
 *   the request and the reload left a pebble on screen that the server had
 *   already dropped. It runs in [viewModelScope] under `NonCancellable` now.
 *
 * A real NavHost destination — its ViewModel dies with the back stack entry, so
 * there is nothing to reset. The form it hosts as a cover is a different story;
 * see [SoulFormViewModel].
 */
@HiltViewModel
class SoulDetailViewModel
    @Inject
    constructor(
        private val soulsService: SoulsServicing,
        private val moments: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private var soulId: String? = null
        private var soul: SoulWithGlyph? = null
        private var loadedMoments: List<PathMoment> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<SoulDetailUiState>(SoulDetailUiState.Loading)
        val uiState: StateFlow<SoulDetailUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(SoulDetailCovers())
        val covers: StateFlow<SoulDetailCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        /**
         * Load [id], unless it is the one already loaded.
         *
         * The NavHost hands the screen only an id, so the screen asks for its
         * soul here rather than receiving the row from the list (the named
         * deviation from iOS, kept).
         */
        fun start(id: String) {
            if (soulId == id) return
            soulId = id
            load()
        }

        fun retry() = load()

        /**
         * The destination came back to the foreground.
         *
         * `SoulForm` is a separate entry now (#852), with no callback
         * back into this instance, so an edit made there has to be picked up by
         * a resume — same mechanism as [SoulsListViewModel.onResumed]. The first
         * resume is skipped because [start] has already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        private fun load() {
            val id = soulId ?: return
            loadJob?.cancel()
            _uiState.value = SoulDetailUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch(id) }
        }

        /**
         * Refresh after a write, keeping the content that is already on screen.
         *
         * Called by [confirmDelete] and [onResumed].
         */
        private fun reload() {
            val id = soulId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable {
                soulsService.loadSoul(id) to moments.loadMomentsWithSoul(id).map { it.toPathMoment() }
            }.fold(
                onSuccess = { (loadedSoul, fetchedMoments) ->
                    soul = loadedSoul
                    loadedMoments = fetchedMoments
                    hasFailed = false
                    isLoaded = true
                },
                onFailure = {
                    Log.e(TAG, "soul detail load failed", it)
                    // A failed post-write refresh keeps the detail that is
                    // already up, rather than replacing it with the error state.
                    if (!isLoaded) hasFailed = true
                },
            )
            publish()
        }

        private fun publish() {
            val current = soul
            _uiState.value =
                when {
                    hasFailed -> SoulDetailUiState.Error(R.string.soul_detail_load_error)
                    !isLoaded || current == null -> SoulDetailUiState.Loading
                    else -> SoulDetailUiState.Content(soul = current, moments = loadedMoments)
                }
        }

        // MARK: - Delete

        fun requestDelete(moment: PathMoment) = _covers.update { it.copy(pendingDeletion = moment) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writes.deleteMoment(target.id)
                        refs.refreshSouls()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete moment failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
