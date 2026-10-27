package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.delete
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.PathMoment
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

/** One collection and the moments whose pebble is in it, newest first (#849, R9). */
sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : CollectionDetailUiState

    data class Content(
        val collection: Collection,
        val moments: List<PathMoment>,
        val zone: ZoneId,
    ) : CollectionDetailUiState {
        /**
         * Moments bucketed by calendar month, newest month first.
         *
         * A getter on the state rather than a `remember(moments)` in the screen:
         * the grouping is a property of this state value, so it is computed once
         * per load instead of once per composition that misses the cache.
         */
        val groups: List<Pair<YearMonth, List<PathMoment>>> by lazy {
            groupPebblesByMonth(moments, zone)
        }
    }
}

/** The delete dialogs. */
data class CollectionDetailCovers(
    val pendingDeletion: PathMoment? = null,
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
        private val moments: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val refs: ReferenceDataServicing,
        private val haptics: HapticsPreference,
    ) : ViewModel() {
        private val zone: ZoneId = ZoneId.systemDefault()

        private var collectionId: String? = null
        private var collection: Collection? = null
        private var loadedMoments: List<PathMoment> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
        val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

        /** Pebbles' haptics switch (#1242 D23), for the rows' long-press buzz (iOS `pebblesContextMenu`). */
        val hapticsOn: Boolean get() = haptics.isOn

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
         * `CollectionForm` is a separate entry now (#852), with no
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
         * Called by [confirmDelete] and [onResumed].
         */
        private fun reload() {
            val id = collectionId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable {
                collectionsService.loadCollection(id) to moments.loadMomentsInCollection(id).map { it.toPathMoment() }
            }.fold(
                onSuccess = { (loadedCollection, fetchedMoments) ->
                    collection = loadedCollection
                    loadedMoments = fetchedMoments
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
                            moments = loadedMoments,
                            zone = zone,
                        )
                }
        }

        // MARK: - Delete

        fun requestDelete(moment: PathMoment) = _covers.update { it.copy(pendingDeletion = moment) }

        fun cancelDelete() = _covers.update { it.copy(pendingDeletion = null) }

        fun dismissDeleteError() = _covers.update { it.copy(didDeleteFail = false) }

        /** R8: the whole moment, or only its pebble; either way the row leaves this list. */
        fun confirmDelete(choice: MomentDeleteChoice) {
            val target = _covers.value.pendingDeletion ?: return
            _covers.update { it.copy(pendingDeletion = null) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        writes.delete(target.id, target.pebble?.id, choice)
                        refs.refreshCollections()
                    }.fold(
                        onSuccess = { reload() },
                        onFailure = {
                            Log.e(TAG, "delete ($choice) failed", it)
                            _covers.update { covers -> covers.copy(didDeleteFail = true) }
                        },
                    )
                }
            }
        }
    }
