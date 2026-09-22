package app.pbbls.android.features.connections

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AcceptInviteResult
import app.pbbls.android.core.data.ConnectionsServicing
import app.pbbls.android.core.data.InvitePreview
import app.pbbls.android.core.data.connectionsErrorMessage
import app.pbbls.android.core.data.toDataError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "connections-accept"

/**
 * The accept surface's four real states (#849).
 *
 * It was five independent `remember`s — `preview`, `accepted`, `isLoading`,
 * `isAccepting`, `errorRes` — which between them describe thirty-odd
 * combinations of which these four are reachable. "Accepted **and** accepting"
 * and "preview **and** accepted" were both representable and both meaningless.
 *
 * [Ready] carries its own `acceptErrorRes` rather than a sibling case: a failed
 * accept leaves the preview on screen with the error under it, because the
 * token may well still be good (the failure is usually the network).
 */
sealed interface AcceptInviteUiState {
    data object Loading : AcceptInviteUiState

    /** The preview itself failed — there is nothing to show and nothing to accept. */
    data class Error(
        @StringRes val messageRes: Int,
    ) : AcceptInviteUiState

    data class Ready(
        val preview: InvitePreview,
        val isAccepting: Boolean = false,
        @StringRes val acceptErrorRes: Int? = null,
    ) : AcceptInviteUiState

    data class Accepted(
        val result: AcceptInviteResult,
    ) : AcceptInviteUiState
}

/**
 * State holder for the invite-accept surface (#849).
 *
 * **The write this moves.** Accepting is the mutual consent, and the RPC
 * consumes the token. `onAccept` ran in `rememberCoroutineScope`, so leaving
 * between the RPC landing and the assignment cancelled it after the connection
 * already existed: the user saw no confirmation, the peer appeared with no
 * explanation, and re-opening the same link took the `alreadyConnected` path
 * that is meant for a re-scanned QR. It runs in [viewModelScope] under
 * `withContext(NonCancellable)` now.
 *
 * A [app.pbbls.android.navigation.PebblesKey.AcceptInvite] nav entry (#852):
 * `hiltViewModel()` is scoped to the entry, so this instance is destroyed when
 * it pops and a second invite link always gets a fresh one. [start] is still
 * guarded on the token, against a re-fired `LaunchedEffect` within one
 * instance.
 */
@HiltViewModel
class AcceptInviteViewModel
    @Inject
    constructor(
        private val service: ConnectionsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AcceptInviteUiState>(AcceptInviteUiState.Loading)
        val uiState: StateFlow<AcceptInviteUiState> = _uiState.asStateFlow()

        private var startedToken: String? = null
        private var loadJob: Job? = null

        /** Preview [token], unless it is the one already previewed. */
        fun start(token: String) {
            if (startedToken == token) return
            startedToken = token
            load(token)
        }

        fun retry() = startedToken?.let { load(it) }

        private fun load(token: String) {
            loadJob?.cancel()
            _uiState.value = AcceptInviteUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { service.preview(token) }
                        .fold(
                            // A withdrawn or expired token is a valid *answer*,
                            // not a failure — the RPC never raises for it — so it
                            // is Ready with an invalid preview, and the content
                            // layer renders the dark copy.
                            onSuccess = { _uiState.value = AcceptInviteUiState.Ready(it) },
                            onFailure = {
                                Log.e(TAG, "invite preview failed", it)
                                _uiState.value =
                                    AcceptInviteUiState.Error(connectionsErrorMessage(it.toDataError()))
                            },
                        )
                }
        }

        /**
         * The explicit consent tap. Never fired automatically (design D5/D12),
         * which is why it is a method and not part of [start].
         */
        fun accept() {
            val token = startedToken ?: return
            val current = _uiState.value
            if (current !is AcceptInviteUiState.Ready || current.isAccepting) return
            _uiState.value = current.copy(isAccepting = true, acceptErrorRes = null)

            // NonCancellable: the RPC has real side effects (spends nothing here,
            // but records a connection), so a dismiss mid-request must not drop
            // the write. A write that lands after the entry has popped only
            // updates an orphaned StateFlow nobody is collecting anymore — this
            // ViewModel is nav-entry-scoped (#852), so there is no next invite
            // link to leak into, unlike when this was an activity-scoped cover.
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { service.accept(token) }
                        .fold(
                            onSuccess = { _uiState.value = AcceptInviteUiState.Accepted(it) },
                            onFailure = {
                                Log.e(TAG, "invite accept failed", it)
                                val res = connectionsErrorMessage(it.toDataError())
                                _uiState.value = current.copy(isAccepting = false, acceptErrorRes = res)
                            },
                        )
                }
            }
        }
    }
