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
 * State holder for the read cover (#849).
 *
 * A pure read, so there is no write to make uncancellable here — what it fixes
 * is rotation: the load lived in a `LaunchedEffect` over `remember(pebbleId)`,
 * so turning the phone re-fetched the pebble and flashed the spinner back over
 * a page the user was reading.
 */
@HiltViewModel
class PebbleDetailViewModel
    @Inject
    constructor(
        private val detailService: PebbleDetailServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PebbleDetailUiState>(PebbleDetailUiState.Loading)
        val uiState: StateFlow<PebbleDetailUiState> = _uiState.asStateFlow()

        private var loaded: Pair<String, Int>? = null
        private var loadJob: Job? = null

        /**
         * Load [pebbleId], unless this exact (id, [reloadKey]) pair is already
         * shown.
         *
         * The key is the host's way of saying "read it again" after an edit
         * saved — the pebble is the same, its contents are not. Keying on the
         * pair rather than on the id alone is what lets a rotation re-run the
         * screen's effect without re-fetching, while an edit still refreshes.
         */
        fun start(
            pebbleId: String,
            reloadKey: Int,
        ) {
            val requested = pebbleId to reloadKey
            if (loaded == requested) return
            loaded = requested
            load(pebbleId)
        }

        fun retry() = loaded?.let { (pebbleId, _) -> load(pebbleId) }

        private fun load(pebbleId: String) {
            loadJob?.cancel()
            _uiState.value = PebbleDetailUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { detailService.load(pebbleId) }
                        .fold(
                            onSuccess = { _uiState.value = PebbleDetailUiState.Content(it) },
                            onFailure = {
                                Log.e(TAG, "pebble detail load failed", it)
                                _uiState.value = PebbleDetailUiState.Error
                            },
                        )
                }
        }
    }
