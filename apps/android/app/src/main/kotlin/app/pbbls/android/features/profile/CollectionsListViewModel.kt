package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.model.Collection
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

private const val TAG = "collections-list"

/**
 * The collections list's state (#849).
 *
 * `isRefreshing` rides on [Content] rather than beside the sealed type: a
 * pull-to-refresh can only happen over content, and the pull indicator is
 * exactly "content, plus a spinner at the top". As a sibling flag it would have
 * admitted "refreshing while erroring", which the screen would have had to
 * disambiguate by branch order.
 */
sealed interface CollectionsListUiState {
    data object Loading : CollectionsListUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : CollectionsListUiState

    data class Content(
        val collections: List<Collection>,
        val isRefreshing: Boolean = false,
    ) : CollectionsListUiState
}

/** The delete dialogs. */
data class CollectionsListCovers(
    val pendingDeletion: Collection? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the collections list (#849).
 *
 * Same delete hole as the souls grid, and the same fix: `delete` ran in
 * `rememberCoroutineScope`, so leaving between the request and the cache
 * refresh left a collection the server had dropped still offered by the
 * composer's collection picker. Both run in [viewModelScope] under
 * `NonCancellable`.
 *
 * A real NavHost destination, so nothing to reset.
 */
@HiltViewModel
class CollectionsListViewModel
    @Inject
    constructor(
        private val collectionsService: CollectionsServicing,
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private var collections: List<Collection> = emptyList()
        private var isRefreshing = false
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<CollectionsListUiState>(CollectionsListUiState.Loading)
        val uiState: StateFlow<CollectionsListUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(CollectionsListCovers())
        val covers: StateFlow<CollectionsListCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            cancelLoad()
            _uiState.value = CollectionsListUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        /** Refresh after a write, keeping the list that is already on screen. */
        private fun reload() {
            cancelLoad()
            loadJob = viewModelScope.launch { fetch() }
        }

        /** The pull gesture: same fetch, with the indicator held up for its duration. */
        fun refresh() {
            cancelLoad()
            isRefreshing = true
            publish()
            loadJob =
                viewModelScope.launch {
                    fetch()
                    isRefreshing = false
                    publish()
                }
        }

        /**
         * Stop the fetch in flight, and lower the pull indicator with it.
         *
         * The indicator was raised by [refresh] and is lowered in that
         * coroutine's tail, which cancelling skips — so without this it stays up
         * forever. Reachable: pull to refresh on a slow network, then long-press
         * a row and confirm the delete. The delete's `reload` cancels the
         * refresh, the list comes back correct, and the spinner never stops.
         */
        private fun cancelLoad() {
            loadJob?.cancel()
            isRefreshing = false
        }

        private suspend fun fetch() {
            runCatchingCancellable { collectionsService.list() }
                .fold(
                    onSuccess = {
                        collections = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "collections fetch failed", it)
                        // A failed refresh keeps the list that is already up.
                        // The screen this replaces cleared its error flag at the
                        // top of every fetch, so a pull-to-refresh that lost the
                        // network replaced the user's collections with the error
                        // screen. A background reload is not the screen failing.
                        if (!isLoaded) hasFailed = true
                    },
                )
            publish()
        }

        private fun publish() {
            _uiState.value =
                when {
                    hasFailed -> CollectionsListUiState.Error(R.string.collections_load_error)
                    !isLoaded -> CollectionsListUiState.Loading
                    else -> CollectionsListUiState.Content(collections, isRefreshing)
                }
        }

        // MARK: - Lifecycle

        /**
         * The destination came back to the foreground.
         *
         * Before the migration this screen kept everything in `remember`, so
         * navigating to a collection detail took it out of composition and popping back
         * built it again — re-running the load. The ViewModel is scoped to the
         * `NavBackStackEntry`, which *survives* that round trip, so `init` alone
         * would leave a collection renamed or emptied in the detail showing its old
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

        fun requestDelete(collection: Collection) = _covers.update { it.copy(pendingDeletion = collection) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        collectionsService.delete(target.id)
                        refs.refreshCollections()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete collection failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
