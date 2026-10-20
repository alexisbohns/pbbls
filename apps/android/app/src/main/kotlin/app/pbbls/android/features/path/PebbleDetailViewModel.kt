package app.pbbls.android.features.path

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.data.delete
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.MomentDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "pebble-detail"

sealed interface PebbleDetailUiState {
    data object Loading : PebbleDetailUiState

    data object Error : PebbleDetailUiState

    data class Content(
        val detail: MomentDetail,
    ) : PebbleDetailUiState
}

sealed interface PebbleDetailEffect {
    /** The moment is gone (`delete_moment`): the page closes. */
    data object MomentDeleted : PebbleDetailEffect
}

/**
 * State holder for the moment read page (#849, #852, #1048). The key carries
 * a moment id; the moment may have no pebble yet.
 *
 * What it fixes is rotation: the load lived in a `LaunchedEffect` over
 * `remember(pebbleId)`, so turning the phone re-fetched the moment and flashed
 * the spinner back over a page the user was reading.
 *
 * Edit moment and Edit pebble are separate entries (#852, R5), with no
 * callback back into this instance, so an edit made there has to be picked up
 * by [onResumed] — same
 * mechanism as `SoulDetailViewModel.onResumed`.
 *
 * Delete lives in the ⋮ menu (R7) and asks what to delete (R8): deleting the
 * moment closes the page ([PebbleDetailEffect.MomentDeleted]); deleting only
 * its pebble reloads it as a bare moment.
 */
@HiltViewModel
class PebbleDetailViewModel
    @Inject
    constructor(
        private val moments: MomentReadServicing,
        private val writes: MomentWriteServicing,
        /**
         * The photo-ratio probe the card's recto reads (#1135), the same
         * process-wide cache the Spine fills, so a card opened from the Spine
         * finds its ratio already known.
         */
        val snapAspect: SnapAspectCache,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PebbleDetailUiState>(PebbleDetailUiState.Loading)
        val uiState: StateFlow<PebbleDetailUiState> = _uiState.asStateFlow()

        private val _effects = UiEffects<PebbleDetailEffect>(viewModelScope)
        val effects: Flow<PebbleDetailEffect> = _effects.flow

        private val _pendingDelete = MutableStateFlow(false)
        val pendingDelete: StateFlow<Boolean> = _pendingDelete.asStateFlow()

        private val _didDeleteFail = MutableStateFlow(false)
        val didDeleteFail: StateFlow<Boolean> = _didDeleteFail.asStateFlow()

        private var momentId: String? = null
        private var detail: MomentDetail? = null
        private var hasFailed = false
        private var isLoaded = false
        private var loadJob: Job? = null
        private var resumeCount = 0

        /** Load [id], unless it is the one already loaded. */
        fun start(id: String) {
            if (momentId == id) return
            momentId = id
            load()
        }

        fun retry() = load()

        /**
         * The entry came back to the foreground, e.g. from an edit entry popping
         * back onto this one. The first resume is skipped because [start] has
         * already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        private fun load() {
            val id = momentId ?: return
            loadJob?.cancel()
            _uiState.value = PebbleDetailUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch(id) }
        }

        /** Refresh after a resume, keeping the content that is already on screen. */
        private fun reload() {
            val id = momentId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable { moments.loadMoment(id) }
                .fold(
                    onSuccess = {
                        detail = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "moment load failed", it)
                        // A failed post-resume refresh keeps the detail that is
                        // already up, rather than replacing it with the error state.
                        if (!isLoaded) hasFailed = true
                    },
                )
            publish()
        }

        private fun publish() {
            val current = detail
            _uiState.value =
                when {
                    hasFailed -> PebbleDetailUiState.Error
                    !isLoaded || current == null -> PebbleDetailUiState.Loading
                    else -> PebbleDetailUiState.Content(current)
                }
        }

        // MARK: - Delete

        fun requestDelete() {
            if (detail != null) _pendingDelete.value = true
        }

        fun cancelDelete() {
            _pendingDelete.value = false
        }

        fun dismissDeleteError() {
            _didDeleteFail.value = false
        }

        /**
         * The R8 choice. Uncancellable once sent: closing the page mid-request
         * must not skip the reload, or leave a write the server accepted unseen.
         */
        fun confirmDelete(choice: MomentDeleteChoice) {
            val target = detail ?: return
            _pendingDelete.value = false
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { writes.delete(target.id, target.pebble?.id, choice) }
                        .fold(
                            onSuccess = {
                                when (choice) {
                                    MomentDeleteChoice.MOMENT -> _effects.emit(PebbleDetailEffect.MomentDeleted)
                                    MomentDeleteChoice.PEBBLE_ONLY -> reload()
                                }
                            },
                            onFailure = {
                                Log.e(TAG, "delete ($choice) failed", it)
                                _didDeleteFail.value = true
                            },
                        )
                }
            }
        }
    }
