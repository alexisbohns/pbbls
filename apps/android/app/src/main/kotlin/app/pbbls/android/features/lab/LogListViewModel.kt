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
        val mode: LogListMode,
        val logs: List<Log>,
        val reactedIds: Set<String>,
    ) : LogListUiState
}

/**
 * State holder for the see-all list (#849, #852 Task 19).
 *
 * The same optimistic reaction toggle as [LabViewModel], with the same hole:
 * the revert ran in `rememberCoroutineScope`, and this used to be a *cover*,
 * so simply closing it cancelled the revert and left a rejected reaction
 * showing as registered underneath. Request and revert are one
 * `withContext(NonCancellable)` step now.
 *
 * **A real Nav3 entry now, not a cover.** [start] takes the raw
 * `PebblesKey.LabLogList.mode` string and maps it back to [LogListMode] with
 * [LogListMode.valueOf] — caught, not propagated: a value that does not match
 * any constant (a stale persisted key from a dropped one, or tampering)
 * publishes [LogListUiState.Error] rather than crashing or silently falling
 * back to the first constant, which is the `AuthMode.fromRoute` bug this
 * migration deleted elsewhere.
 *
 * **`finish()` is gone, not just its `BackHandler` call site.** It used to
 * release the mode guard so the next *presentation* would re-read — needed
 * only because a cover's `hiltViewModel()` binds to the host destination's
 * back stack entry, which outlives the cover's own open/close cycle many
 * times over. An entry's ViewModel is destroyed when the entry is popped
 * (`rememberViewModelStoreNavEntryDecorator`, wired in `RootScreen`), so the
 * next presentation is always a fresh instance with `startedMode == null` —
 * there is nothing left to release. [InviteViewModel] was promoted to an
 * entry in Task 17 under the same decorator and has the identical shape
 * (`hasStarted` + a `finish()` called from `InviteScreen.dismiss()`), so the
 * same reasoning applies there too; that cleanup was out of scope here and is
 * flagged in the PR rather than made in this file (root `CLAUDE.md`: no
 * refactors without approval).
 */
@HiltViewModel
class LogListViewModel
    @Inject
    constructor(
        private val logsService: LogsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LogListUiState>(LogListUiState.Loading)
        val uiState: StateFlow<LogListUiState> = _uiState.asStateFlow()

        private var startedMode: String? = null
        private var loadJob: Job? = null

        /** Load [rawMode], unless it is the one already loaded. */
        fun start(rawMode: String) {
            if (startedMode == rawMode) return
            startedMode = rawMode
            load(rawMode)
        }

        fun retry() = startedMode?.let { load(it) }

        /** Cover-image URL for [log] — a pure projection, not a call. */
        fun coverImageUrl(log: Log): String? = logsService.coverImageUrl(log)

        private fun load(rawMode: String) {
            loadJob?.cancel()
            val mode = runCatching { LogListMode.valueOf(rawMode) }.getOrNull()
            if (mode == null) {
                AndroidLog.e(TAG, "unknown LogListMode: $rawMode")
                _uiState.value = LogListUiState.Error(R.string.lab_list_load_error)
                return
            }
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
                            _uiState.value = LogListUiState.Content(mode = mode, logs = logs, reactedIds = reactions)
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
            _uiState.value = current.copy(logs = next.logs, reactedIds = next.reactedIds)

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
                        _uiState.value = shown.copy(logs = reverted.logs, reactedIds = reverted.reactedIds)
                    }
                }
            }
        }
    }
