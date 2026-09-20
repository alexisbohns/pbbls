package app.pbbls.android.features.lab

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.ReactionToggle
import app.pbbls.android.features.lab.services.LogsServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.util.Log as AndroidLog

private const val TAG = "lab-log-list"

/**
 * One full feed, seen through the Lab's "see all" (#849).
 *
 * The feed and the reactions load together and fail together here — unlike the
 * Lab page, where four independent feeds each own their failure. A list with no
 * rows is not a list.
 */
sealed interface LogListUiState {
    data object Loading : LogListUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : LogListUiState

    data class Content(
        val logs: List<Log>,
        val reactedIds: Set<String>,
    ) : LogListUiState
}

/**
 * State holder for the see-all cover (#849).
 *
 * The same optimistic reaction toggle as [LabViewModel], with the same hole:
 * the revert ran in `rememberCoroutineScope`, and this is a *cover*, so simply
 * closing it cancelled the revert and left a rejected reaction showing as
 * registered underneath. Request and revert are one `withContext(NonCancellable)`
 * step now.
 *
 * A cover inside the Lab destination, so [start] is guarded on the mode and
 * [finish] releases it — the ViewModel outlives the presentation, and opening
 * "see all" on the backlog after the changelog must not show the changelog.
 */
@HiltViewModel
class LogListViewModel
    @Inject
    constructor(
        private val logsService: LogsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LogListUiState>(LogListUiState.Loading)
        val uiState: StateFlow<LogListUiState> = _uiState.asStateFlow()

        private var startedMode: LogListMode? = null
        private var loadJob: Job? = null

        /** Load [mode], unless it is the one already loaded. */
        fun start(mode: LogListMode) {
            if (startedMode == mode) return
            startedMode = mode
            load(mode)
        }

        fun retry() = startedMode?.let { load(it) }

        /** Cover-image URL for [log] — a pure projection, not a call. */
        fun coverImageUrl(log: Log): String? = logsService.coverImageUrl(log)

        private fun load(mode: LogListMode) {
            loadJob?.cancel()
            _uiState.value = LogListUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable {
                        coroutineScope {
                            val feed =
                                async {
                                    when (mode) {
                                        LogListMode.CHANGELOG -> logsService.changelog(null)
                                        LogListMode.BACKLOG -> logsService.backlog(null)
                                    }
                                }
                            val reactions = async { logsService.myReactions() }
                            feed.await() to reactions.await()
                        }
                    }.fold(
                        onSuccess = { (logs, reactions) ->
                            _uiState.value = LogListUiState.Content(logs = logs, reactedIds = reactions)
                        },
                        onFailure = {
                            AndroidLog.e(TAG, "list fetch failed", it)
                            _uiState.value = LogListUiState.Error(R.string.lab_list_load_error)
                        },
                    )
                }
        }

        fun toggleReaction(log: Log) {
            val current = _uiState.value as? LogListUiState.Content ?: return
            val before = ReactionToggle.State(reactedIds = current.reactedIds, logs = current.logs)
            val wasReacted = ReactionToggle.wasReacted(before, log.id)
            val next = ReactionToggle.toggle(before, log.id)
            _uiState.value = LogListUiState.Content(logs = next.logs, reactedIds = next.reactedIds)

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        if (wasReacted) logsService.unreact(log.id) else logsService.react(log.id)
                    }.onFailure {
                        AndroidLog.e(TAG, "reaction toggle failed", it)
                        val shown = _uiState.value as? LogListUiState.Content ?: return@onFailure
                        val reverted =
                            ReactionToggle.revert(
                                ReactionToggle.State(reactedIds = shown.reactedIds, logs = shown.logs),
                                log.id,
                                wasReacted,
                            )
                        _uiState.value =
                            LogListUiState.Content(logs = reverted.logs, reactedIds = reverted.reactedIds)
                    }
                }
            }
        }

        /** Release the mode guard so the next presentation re-reads. */
        fun finish() {
            startedMode = null
        }
    }
