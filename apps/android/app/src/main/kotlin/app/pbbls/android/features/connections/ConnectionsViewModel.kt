package app.pbbls.android.features.connections

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.services.Connection
import app.pbbls.android.services.ConnectionsServicing
import app.pbbls.android.services.connectionsErrorMessage
import app.pbbls.android.services.toDataError
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "connections"

/** The people you are connected with (#849). */
sealed interface ConnectionsUiState {
    data object Loading : ConnectionsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : ConnectionsUiState

    data class Content(
        val connections: List<Connection>,
    ) : ConnectionsUiState
}

/** The removal dialogs. */
data class ConnectionsCovers(
    val pendingRemoval: Connection? = null,
    @StringRes val removeErrorRes: Int? = null,
)

/**
 * State holder for the connections list (#849).
 *
 * **The write this moves.** The removal is optimistic — the row leaves the list
 * before the request, and a failure reloads the truth back in. That reconcile
 * ran in `rememberCoroutineScope`, so leaving the screen mid-flight cancelled
 * it after the RPC had already severed the connection for both sides. The
 * severing is real either way; what was lost is the *reconcile*, so a failure
 * silently kept a connection on screen that no longer existed, and a success
 * never confirmed. The request and its reconcile are one
 * `withContext(NonCancellable)` step in [viewModelScope] now.
 *
 * No resume refresh, unlike the souls and collections lists — not because the
 * list cannot go stale, but because a resume hook would not fix the case where
 * it does. This destination has no child to come back from; the one way a
 * connection appears while the list is up is accepting an invite link, and that
 * surface sits *above* the nav host, so this entry never leaves RESUMED and no
 * resume event fires. The new peer is therefore missing until the list is left
 * and re-entered. Unchanged from before the migration, and it wants a signal
 * from the accept surface rather than a lifecycle hook — filed rather than
 * papered over.
 */
@HiltViewModel
class ConnectionsViewModel
    @Inject
    constructor(
        private val service: ConnectionsServicing,
    ) : ViewModel() {
        private var connections: List<Connection> = emptyList()
        private var hasFailed = false
        private var isLoaded = false

        private val _uiState = MutableStateFlow<ConnectionsUiState>(ConnectionsUiState.Loading)
        val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

        private val _covers = MutableStateFlow(ConnectionsCovers())
        val covers: StateFlow<ConnectionsCovers> = _covers.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            loadJob?.cancel()
            _uiState.value = ConnectionsUiState.Loading
            isLoaded = false
            loadJob = viewModelScope.launch { fetch() }
        }

        private suspend fun fetch() {
            runCatchingCancellable { service.list() }
                .fold(
                    onSuccess = {
                        connections = it
                        hasFailed = false
                        isLoaded = true
                    },
                    onFailure = {
                        Log.e(TAG, "connections load failed", it)
                        // A failed reconcile keeps the list that is already up.
                        if (!isLoaded) hasFailed = true
                    },
                )
            publish()
        }

        private fun publish() {
            _uiState.value =
                when {
                    hasFailed -> ConnectionsUiState.Error(R.string.connections_load_error)
                    !isLoaded -> ConnectionsUiState.Loading
                    else -> ConnectionsUiState.Content(connections)
                }
        }

        // MARK: - Removal

        fun requestRemoval(row: Connection) = _covers.update { it.copy(pendingRemoval = row) }

        fun cancelRemoval() = _covers.update { it.copy(pendingRemoval = null) }

        fun dismissRemoveError() = _covers.update { it.copy(removeErrorRes = null) }

        /**
         * Confirmed removal, optimistic. The row leaves immediately; a failure
         * puts the error up and reloads the server's answer back in.
         *
         * `NonCancellable` because there is no consistent point to stop at once
         * the RPC has left: the connection is severed for the peer too, and a
         * client that skipped the reconcile is showing a relationship that no
         * longer exists on either side.
         */
        fun confirmRemoval(block: Boolean) {
            val target = _covers.value.pendingRemoval ?: return
            _covers.update { it.copy(pendingRemoval = null) }
            connections = connections.filterNot { it.connectionId == target.connectionId }
            publish()

            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        service.remove(connectionId = target.connectionId, block = block)
                    }.onFailure {
                        Log.e(TAG, "connection removal failed", it)
                        val res = connectionsErrorMessage(it.toDataError())
                        _covers.update { covers -> covers.copy(removeErrorRes = res) }
                        // `fetch()` directly, not `reload()`: reload launches a
                        // fresh child of viewModelScope, which NonCancellable
                        // does not cover, so the reconcile would be cancelled
                        // exactly in the case this block exists for.
                        loadJob?.cancel()
                        fetch()
                    }
                }
            }
        }
    }
