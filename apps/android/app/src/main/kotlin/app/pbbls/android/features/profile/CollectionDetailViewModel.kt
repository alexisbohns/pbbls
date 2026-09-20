package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.CollectionsServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ReferenceDataServicing
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
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "collection-detail"

/** One collection and the pebbles in it (#849). */
sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : CollectionDetailUiState

    data class Content(
        val collection: Collection,
        val pebbles: List<Pebble>,
        val zone: ZoneId,
    ) : CollectionDetailUiState {
        /**
         * Pebbles bucketed by calendar month, newest month first.
         *
         * A getter on the state rather than a `remember(pebbles)` in the screen:
         * the grouping is a property of this state value, so it is computed once
         * per load instead of once per composition that misses the cache.
         */
        val groups: List<Pair<YearMonth, List<Pebble>>> by lazy {
            groupPebblesByMonth(pebbles, zone)
        }
    }
}

/** The pebble-edit cover and the delete dialogs. */
data class CollectionDetailCovers(
    val editingPebbleId: String? = null,
    val pendingDeletion: Pebble? = null,
    val didDeleteFail: Boolean = false,
)

/**
 * State holder for the pushed collection detail (#849).
 *
 * The soul detail's twin, with the month grouping folded into the state — see
 * [SoulDetailViewModel] for the rotation and delete-cancellation notes, which
 * apply here verbatim.
 *
 * `zone` is captured once at construction, as the screen's `remember` did: a
 * list that silently re-bucketed itself because the device crossed a timezone
 * mid-session would be more surprising than one that is stale until reload.
 */
@HiltViewModel
class CollectionDetailViewModel
    @Inject
    constructor(
        private val collectionsService: CollectionsServicing,
        private val writeService: PebbleWriteServicing,
        private val refs: ReferenceDataServicing,
    ) : ViewModel() {
        private val zone: ZoneId = ZoneId.systemDefault()

        private var collectionId: String? = null
        private var collection: Collection? = null
        private var pebbles: List<Pebble> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
        val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(CollectionDetailCovers())
        val covers: StateFlow<CollectionDetailCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null
        private var resumeCount = 0

        /** Load [id], unless it is the one already loaded. */
        fun start(id: String) {
            if (collectionId == id) return
            collectionId = id
            load()
        }

        fun retry() = load()

        /**
         * The destination came back to the foreground.
         *
         * `CollectionForm` is a separate entry now (#852 Task 17), with no
         * callback back into this instance, so an edit made there has to be
         * picked up by a resume — see [SoulDetailViewModel.onResumed]. The first
         * resume is skipped because [start] has already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        private fun load() {
            val id = collectionId ?: return
            loadJob?.cancel()
            _uiState.value = CollectionDetailUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch(id) }
        }

        /**
         * Refresh after a write, keeping the content that is already on screen.
         *
         * Called by [onPebbleSaved], [confirmDelete] and [onResumed].
         */
        private fun reload() {
            val id = collectionId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable {
                collectionsService.loadCollection(id) to collectionsService.loadPebbles(id)
            }.fold(
                onSuccess = { (loadedCollection, loadedPebbles) ->
                    collection = loadedCollection
                    pebbles = loadedPebbles
                    hasFailed = false
                    isLoaded = true
                },
                onFailure = {
                    Log.e(TAG, "collection detail load failed", it)
                    // As the soul detail: a failed post-write refresh keeps the
                    // content already on screen.
                    if (!isLoaded) hasFailed = true
                },
            )
            publish()
        }

        private fun publish() {
            val current = collection
            _uiState.value =
                when {
                    // The collection detail reuses the soul detail's copy, as the
                    // screen it replaces did.
                    hasFailed -> CollectionDetailUiState.Error(R.string.soul_detail_load_error)
                    !isLoaded || current == null -> CollectionDetailUiState.Loading
                    else ->
                        CollectionDetailUiState.Content(
                            collection = current,
                            pebbles = pebbles,
                            zone = zone,
                        )
                }
        }

        // MARK: - Covers

        fun openPebble(pebbleId: String) = _covers.update { it.copy(editingPebbleId = pebbleId) }

        fun closePebble() = _covers.update { it.copy(editingPebbleId = null) }

        fun onPebbleSaved() {
            _covers.update { it.copy(editingPebbleId = null) }
            reload()
        }

        // MARK: - Delete

        fun requestDelete(pebble: Pebble) = _covers.update { it.copy(pendingDeletion = pebble) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        fun confirmDelete() {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writeService.delete(target.id)
                        refs.refreshCollections()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete pebble failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
