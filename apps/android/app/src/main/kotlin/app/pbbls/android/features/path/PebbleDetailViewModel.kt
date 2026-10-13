package app.pbbls.android.features.path

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.model.MomentDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "pebble-detail"

sealed interface PebbleDetailUiState {
    data object Loading : PebbleDetailUiState

    data object Error : PebbleDetailUiState

    data class Content(
        val detail: MomentDetail,
    ) : PebbleDetailUiState
}

/**
 * State holder for the moment read page (#849, #852, #1048). The key carries
 * a moment id; the moment may have no pebble yet.
 *
 * A pure read, so there is no write to make uncancellable here — what it fixes
 * is rotation: the load lived in a `LaunchedEffect` over `remember(pebbleId)`,
 * so turning the phone re-fetched the moment and flashed the spinner back over
 * a page the user was reading.
 *
 * Edit moment and Edit pebble are separate entries (#852, R5), with no
 * callback back into this instance, so an edit made there has to be picked up
 * by [onResumed] — same
 * mechanism as `SoulDetailViewModel.onResumed`.
 */
@HiltViewModel
class PebbleDetailViewModel
    @Inject
    constructor(
        private val moments: MomentReadServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PebbleDetailUiState>(PebbleDetailUiState.Loading)
        val uiState: StateFlow<PebbleDetailUiState> = _uiState.asStateFlow()

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
    }
