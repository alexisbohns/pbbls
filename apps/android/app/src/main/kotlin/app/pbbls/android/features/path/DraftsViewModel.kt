package app.pbbls.android.features.path

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.services.PebbleDraftsServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "drafts"

sealed interface DraftsUiState {
    data object Loading : DraftsUiState

    data object Error : DraftsUiState

    data class Content(
        val drafts: List<PebbleDraftRecord>,
    ) : DraftsUiState
}

/**
 * State holder for the drafts list (#849).
 *
 * The delete is optimistic — the row leaves immediately and a failure reloads
 * the truth back in — and it ran in `rememberCoroutineScope`. Leaving the cover
 * mid-delete cancelled it after the request had gone, so the row was gone from
 * the server and the list never reloaded: the badge on the Path kept counting a
 * draft that no longer existed until the next cold start. The request and the
 * reconcile are one `withContext(NonCancellable)` step now.
 */
@HiltViewModel
class DraftsViewModel
    @Inject
    constructor(
        private val drafts: PebbleDraftsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<DraftsUiState>(DraftsUiState.Loading)
        val uiState: StateFlow<DraftsUiState> = _uiState.asStateFlow()

        private var loadedKey: Int? = null
        private var loadJob: Job? = null

        /**
         * Load, unless this [reloadKey] is already shown. The host bumps it on
         * every draft write, so the list refreshes on open and after a publish
         * while a rotation re-runs the screen's effect for free.
         */
        fun start(reloadKey: Int) {
            if (loadedKey == reloadKey) return
            loadedKey = reloadKey
            load()
        }

        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            _uiState.value = DraftsUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { drafts.list() }
                        .fold(
                            onSuccess = { _uiState.value = DraftsUiState.Content(it) },
                            onFailure = {
                                Log.e(TAG, "drafts load failed", it)
                                _uiState.value = DraftsUiState.Error
                            },
                        )
                }
        }

        /**
         * Optimistic: the row leaves immediately, and a failure reloads the truth
         * back in rather than trying to guess where it went.
         */
        fun delete(record: PebbleDraftRecord) {
            _uiState.value =
                (_uiState.value as? DraftsUiState.Content)
                    ?.let { DraftsUiState.Content(it.drafts.filterNot { d -> d.id == record.id }) }
                    ?: _uiState.value

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { drafts.delete(record.id) }
                        .onFailure {
                            Log.e(TAG, "draft delete failed", it)
                            load()
                        }
                }
            }
        }
    }
