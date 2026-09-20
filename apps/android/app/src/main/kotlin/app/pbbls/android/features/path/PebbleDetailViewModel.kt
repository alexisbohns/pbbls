package app.pbbls.android.features.path

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.services.PebbleDetailServicing
import app.pbbls.android.ui.runCatchingCancellable
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
        val detail: PebbleDetail,
    ) : PebbleDetailUiState
}

/**
 * State holder for the pushed detail entry (#849, #852).
 *
 * A pure read, so there is no write to make uncancellable here — what it fixes
 * is rotation: the load lived in a `LaunchedEffect` over `remember(pebbleId)`,
 * so turning the phone re-fetched the pebble and flashed the spinner back over
 * a page the user was reading.
 *
 * `EditPebble` is a separate entry now (#852), with no callback back into this
 * instance, so an edit made there has to be picked up by [onResumed] — same
 * mechanism as `SoulDetailViewModel.onResumed`.
 */
@HiltViewModel
class PebbleDetailViewModel
    @Inject
    constructor(
        private val detailService: PebbleDetailServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PebbleDetailUiState>(PebbleDetailUiState.Loading)
        val uiState: StateFlow<PebbleDetailUiState> = _uiState.asStateFlow()

        private var pebbleId: String? = null
        private var detail: PebbleDetail? = null
        private var hasFailed = false
        private var isLoaded = false
        private var loadJob: Job? = null
        private var resumeCount = 0

        /** Load [id], unless it is the one already loaded. */
        fun start(id: String) {
            if (pebbleId == id) return
            pebbleId = id
            load()
        }

        fun retry() = load()

        /**
         * The entry came back to the foreground, e.g. from `EditPebble` popping
         * back onto this one. The first resume is skipped because [start] has
         * already loaded.
         */
        fun onResumed() {
            resumeCount += 1
            if (resumeCount > 1) reload()
        }

        private fun load() {
            val id = pebbleId ?: return
            loadJob?.cancel()
            _uiState.value = PebbleDetailUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch(id) }
        }

        /** Refresh after a resume, keeping the content that is already on screen. */
        private fun reload() {
            val id = pebbleId ?: return
            loadJob?.cancel()
            loadJob = viewModelScope.launch { fetch(id) }
        }

        private suspend fun fetch(id: String) {
            runCatchingCancellable { detailService.load(id) }
                .fold(
                    onSuccess = {
                        detail = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "pebble detail load failed", it)
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
