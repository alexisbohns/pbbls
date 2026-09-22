package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SoulsServicing
import app.pbbls.android.ui.runCatchingCancellable
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

private const val TAG = "souls-list"

/**
 * The souls grid's state (#849).
 *
 * `isLoading` + `loadFailed` + `items` were three independent `remember`s, and
 * the render `when` had to order its branches by hand to keep "not loading, did
 * not fail, no items" from meaning two different things. An empty [Content] is
 * the empty state; failure is its own case.
 */
sealed interface SoulsListUiState {
    data object Loading : SoulsListUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : SoulsListUiState

    data class Content(
        val souls: List<SoulWithGlyph>,
    ) : SoulsListUiState
}

/** The delete dialogs, which stack over the grid. */
data class SoulsListCovers(
    val pendingDeletion: SoulWithGlyph? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the souls grid (#849).
 *
 * **The write this moves.** `delete` ran in `rememberCoroutineScope`, so
 * popping back to Profile between the request landing and the reload left the
 * deleted soul in `ReferenceDataService`'s cached list — which is what the
 * composer's soul picker reads. The row was gone from the server and still
 * offered to tag a pebble with, until the next cold start. The delete and the
 * cache refresh now run in [viewModelScope] under `withContext(NonCancellable)`:
 * once the row is gone there is no consistent point to stop at.
 *
 * A real NavHost destination, so its ViewModel is scoped to the back stack entry
 * and dies with it — no reset needed here, unlike the form cover it hosts.
 */
@HiltViewModel
class SoulsListViewModel
    @Inject
    constructor(
        private val soulsService: SoulsServicing,
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private var souls: List<SoulWithGlyph> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<SoulsListUiState>(SoulsListUiState.Loading)
        val uiState: StateFlow<SoulsListUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(SoulsListCovers())
        val covers: StateFlow<SoulsListCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            _uiState.value = SoulsListUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        /**
         * Refresh after a write, without returning to the spinner: the grid is
         * already on screen and blanking it after a delete reads as a glitch
         * (the same rule `PathViewModel.reload` follows).
         */
        private fun reload() {
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch() }
        }

        private suspend fun fetch() {
            runCatchingCancellable { soulsService.list() }
                .fold(
                    onSuccess = {
                        souls = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "souls fetch failed", it)
                        // A failed refresh keeps the grid that is already up.
                        if (!isLoaded) hasFailed = true
                    },
                )
            publish()
        }

        private fun publish() {
            _uiState.value =
                when {
                    hasFailed -> SoulsListUiState.Error(R.string.souls_load_error)
                    !isLoaded -> SoulsListUiState.Loading
                    else -> SoulsListUiState.Content(souls)
                }
        }

        // MARK: - Lifecycle

        /**
         * The destination came back to the foreground.
         *
         * Before the migration this screen kept everything in `remember`, so
         * navigating to a soul detail took it out of composition and popping back
         * built it again — re-running the load. The ViewModel is scoped to the
         * `NavBackStackEntry`, which *survives* that round trip, so `init` alone
         * would leave a soul renamed or emptied in the detail showing its old
         * name and count, with no gesture that repairs it.
         *
         * The first resume is skipped because `init` has already loaded; every
         * later one is a return, and refreshes without the spinner. Coming back
         * from the background also counts, which is harmless — it is the same
         * silent reload.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        // MARK: - Delete

        fun requestDelete(soul: SoulWithGlyph) = _covers.update { it.copy(pendingDeletion = soul) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        soulsService.delete(target.id)
                        // Inside the uncancellable section on purpose: the cache
                        // is what the composer's picker reads, and a soul that
                        // is gone server-side must not stay offered there.
                        refs.refreshSouls()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete soul failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
