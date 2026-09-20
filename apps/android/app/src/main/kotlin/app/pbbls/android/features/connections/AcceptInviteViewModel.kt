package app.pbbls.android.features.connections

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.services.AcceptInviteResult
import app.pbbls.android.services.ConnectionsServicing
import app.pbbls.android.services.InvitePreview
import app.pbbls.android.services.connectionsErrorMessage
import app.pbbls.android.services.toDataError
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
 * **The most exposed cover in the app.** `RootScreen` composes it *above* the
 * nav host, so `hiltViewModel()` binds it to the activity's store rather than
 * any back-stack entry — it outlives every destination, not just one. [start]
 * is guarded on the token and [finish] is explicit; without them a second
 * invite link in the same session would open onto the first one's result.
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

        /**
         * Bumped by [finish]. Every write carries the epoch it started under and
         * publishes only if it still matches.
         *
         * The accept runs under `withContext(NonCancellable)`, which is what
         * makes it survive the scope dying — and therefore also what stops
         * [finish] from stopping it. Without this fence, backing out of a
         * pending accept lets the late success overwrite the reset, and the next
         * invite link renders at least one frame of "you're connected with
         * <the previous peer>" over a Done button. A tap landing in that frame
         * dismisses and discards the new invite unseen.
         */
        private var epoch = 0

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
            val startedEpoch = epoch

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { service.accept(token) }
                        .fold(
                            onSuccess = {
                                // The request still lands — that is the point of
                                // NonCancellable — but it only reaches the screen
                                // if this surface is still the one it belongs to.
                                if (epoch == startedEpoch) {
                                    _uiState.value = AcceptInviteUiState.Accepted(it)
                                }
                            },
                            onFailure = {
                                Log.e(TAG, "invite accept failed", it)
                                val res = connectionsErrorMessage(it.toDataError())
                                if (epoch == startedEpoch) {
                                    _uiState.value = current.copy(isAccepting = false, acceptErrorRes = res)
                                }
                            },
                        )
                }
            }
        }

        /**
         * Clear for the next invite link. Explicit because this ViewModel is
         * activity-scoped — see the class KDoc.
         *
         * Unlike the form covers, this one *does* reset its state: the host
         * drops the surface in the same tap (`onDismiss` pops the back stack
         * entry), so there is no frame in which the cleared state is still
         * rendered, and leaving a stranger's accepted result in memory for the
         * rest of the session is worse than the alternative.
         */
        fun finish() {
            startedToken = null
            loadJob?.cancel()
            // Fences any accept already in flight — see [epoch].
            epoch += 1
            _uiState.value = AcceptInviteUiState.Loading
        }
    }
