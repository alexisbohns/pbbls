package app.pbbls.android.features.connections

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.ConnectionInvite
import app.pbbls.android.core.data.ConnectionsServicing
import app.pbbls.android.core.data.connectionsErrorMessage
import app.pbbls.android.core.data.toDataError
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

private const val TAG = "connections-invite"

/**
 * Your live invite (#849).
 *
 * `invite` + `isLoading` + `errorRes` were three independent `remember`s, and
 * the render read them in an order that made "no invite, not loading, no error"
 * mean the error copy. The rotation flag rides on [Content] because a rotation
 * can only happen over an invite that is already shown.
 */
sealed interface InviteUiState {
    data object Loading : InviteUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : InviteUiState

    data class Content(
        val invite: ConnectionInvite,
        val isRotating: Boolean = false,
        /**
         * A rotation that failed, over an invite that is still valid. It has no
         * dismissal of its own: the next rotation replaces it, and reopening the
         * cover re-reads and clears it.
         */
        @StringRes val rotateErrorRes: Int? = null,
    ) : InviteUiState
}

/**
 * State holder for the invite cover (#849).
 *
 * **The write this moves, and it is the sharpest one in this part.** Rotating
 * revokes the live token server-side and mints a new one. `onRotate` ran in
 * `rememberCoroutineScope`, so dismissing the cover between the RPC landing and
 * the assignment cancelled the coroutine *after* the old token was already
 * revoked — leaving the screen, and the user's clipboard, holding a link that
 * no longer works, with no error and no sign anything happened. Reopening
 * showed the new token, so the failure was invisible from inside the app and
 * only visible to whoever they had already sent the dead link to. The rotation
 * and the assignment are one `withContext(NonCancellable)` step now.
 *
 * A cover inside `ConnectionsScreen`, which is a NavHost destination, so
 * `hiltViewModel()` binds this to that destination's `NavBackStackEntry` — which
 * outlives the cover's open/close cycle many times over. Hence the guarded
 * [start] and the [finish] reset.
 */
@HiltViewModel
class InviteViewModel
    @Inject
    constructor(
        private val service: ConnectionsServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<InviteUiState>(InviteUiState.Loading)
        val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

        // Guards a double-start within ONE instance (a re-fired LaunchedEffect).
        // It needs no release: Invite is a nav entry (#852), so its ViewModel is
        // destroyed when the entry pops and the next presentation is always a
        // fresh instance. The `finish()` that used to reset this existed only
        // because a cover's ViewModel outlived the cover.
        private var hasStarted = false
        private var loadJob: Job? = null

        /**
         * Fetch the live invite, once per presentation.
         *
         * Guarded so a rotation cannot be undone by the screen's
         * `LaunchedEffect` re-running after a configuration change — the server
         * would hand back the *rotated* token, so the display would be right,
         * but the spinner would flash over a QR the user may be mid-scan on.
         */
        fun start() {
            if (hasStarted) return
            hasStarted = true
            load()
        }

        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            _uiState.value = InviteUiState.Loading
            loadJob =
                viewModelScope.launch {
                    runCatchingCancellable { service.createInvite(rotate = false) }
                        .fold(
                            onSuccess = { _uiState.value = InviteUiState.Content(it) },
                            onFailure = {
                                Log.e(TAG, "invite load failed", it)
                                _uiState.value = InviteUiState.Error(connectionsErrorMessage(it.toDataError()))
                            },
                        )
                }
        }

        /**
         * Revoke the live token and mint a fresh one — the whole revocation
         * surface, so a link already shared stays alive until this is tapped.
         *
         * Uncancellable for the reason in the class KDoc: stopping between the
         * revocation and the new token reaching the screen is the one outcome
         * that leaves the user confidently sharing a dead link.
         */
        fun rotate() {
            val current = _uiState.value
            if (current !is InviteUiState.Content || current.isRotating) return
            _uiState.value = current.copy(isRotating = true, rotateErrorRes = null)

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { service.createInvite(rotate = true) }
                        .fold(
                            onSuccess = { _uiState.value = InviteUiState.Content(it) },
                            onFailure = {
                                Log.e(TAG, "invite rotation failed", it)
                                val res = connectionsErrorMessage(it.toDataError())
                                // The old invite is still what the server has, so
                                // keep showing it rather than dropping to Error.
                                _uiState.value = current.copy(isRotating = false, rotateErrorRes = res)
                            },
                        )
                }
            }
        }
    }
