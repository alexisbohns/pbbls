package app.pbbls.android.features.lab

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.LogsServicing
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log as AndroidLog

private const val TAG = "lab-announcement"

/** What the announcement detail can be showing (#852). */
sealed interface AnnouncementDetailUiState {
    data object Loading : AnnouncementDetailUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : AnnouncementDetailUiState

    data class Content(
        val log: Log,
        val coverUrl: String?,
    ) : AnnouncementDetailUiState
}

/**
 * State holder for the announcement detail (#852).
 *
 * **This screen used to receive a whole [Log] plus a `coverUrl` the Lab page
 * had already computed** — it was a content swap inside `LabScreen`, so the
 * object was simply in hand. A Nav3 key can only carry an id
 * (`PebblesKey.LabAnnouncement.logId`), so this loads its own row by id now,
 * the same deviation from iOS that [SoulDetailViewModel][app.pbbls.android.features.profile.SoulDetailViewModel]
 * already made — iOS's `AnnouncementDetailView` still takes the object
 * directly, because its own cover never lost the reference.
 *
 * A real NavHost destination: its ViewModel dies with the back stack entry,
 * so there is nothing to reset between presentations.
 */
@HiltViewModel
class AnnouncementDetailViewModel
    @Inject
    constructor(
        private val logsService: LogsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AnnouncementDetailUiState>(AnnouncementDetailUiState.Loading)
        val uiState: StateFlow<AnnouncementDetailUiState> = _uiState.asStateFlow()

        private var logId: String? = null
        private var loadJob: Job? = null

        /** Load [id], unless it is the one already loaded. */
        fun start(id: String) {
            if (logId == id) return
            logId = id
            load(id)
        }

        fun retry() = logId?.let { load(it) }

        private fun load(id: String) {
            loadJob?.cancel()
            _uiState.value = AnnouncementDetailUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { logsService.log(id) }
                        .fold(
                            onSuccess = { log ->
                                // A newer `start` (a different id) must not be
                                // clobbered by this stale response landing late.
                                if (logId != id) return@fold
                                if (log == null) {
                                    AndroidLog.e(TAG, "announcement not found: $id")
                                    _uiState.value =
                                        AnnouncementDetailUiState.Error(R.string.lab_announcement_load_error)
                                } else {
                                    _uiState.value =
                                        AnnouncementDetailUiState.Content(
                                            log = log,
                                            coverUrl = logsService.coverImageUrl(log),
                                        )
                                }
                            },
                            onFailure = {
                                AndroidLog.e(TAG, "announcement load failed", it)
                                if (logId != id) return@fold
                                _uiState.value = AnnouncementDetailUiState.Error(announcementErrorMessage(it.toDataError()))
                            },
                        )
                }
        }
    }

/** No named conditions read this row raises — offline gets its own copy, everything else is generic. */
private fun announcementErrorMessage(error: DataError): Int =
    when (error) {
        DataError.Network -> R.string.error_offline
        DataError.Unauthorized, DataError.NotFound, DataError.Quota, is DataError.Conflict, is DataError.Unknown ->
            R.string.lab_announcement_load_error
    }
