package app.pbbls.android.features.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.services.Connection
import app.pbbls.android.services.ConnectionPeer
import app.pbbls.android.testing.FakeConnectionsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The connections list's load, removal and reconcile contract (#849). */
class ConnectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun connection(
        id: String,
        name: String = "Peer $id",
    ) = Connection(
        connectionId = id,
        connectedAt = "2026-09-19T12:00:00Z",
        peer = ConnectionPeer(displayName = name),
    )

    private fun viewModel(service: FakeConnectionsService = FakeConnectionsService()) = ConnectionsViewModel(service)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)

            assertEquals(ConnectionsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as ConnectionsUiState.Content
            assertEquals(listOf("a"), state.connections.map { it.connectionId })
        }

    /** Invite-only discovery means an empty list is the normal first state. */
    @Test
    fun `an empty result is Content, not Error`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as ConnectionsUiState.Content).connections.isEmpty())
        }

    @Test
    fun `a failed load becomes Error, and retry recovers`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            service.failNext = IOException("offline")
            val viewModel = viewModel(service)
            advanceUntilIdle()

            assertEquals(
                R.string.connections_load_error,
                (viewModel.uiState.value as ConnectionsUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ConnectionsUiState.Content)
        }

    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, service.listCount)
        }

    // MARK: - Removal

    /** The row leaves before the request — that is the point of the optimism. */
    @Test
    fun `confirmRemoval drops the row immediately and sends the request`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a"), connection("b")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            viewModel.requestRemoval(connection("a"))
            val pending = viewModel.covers.value.pendingRemoval
            assertEquals("a", pending?.connectionId)

            viewModel.confirmRemoval(block = false)

            // Before the coroutine has even run.
            val state = viewModel.uiState.value as ConnectionsUiState.Content
            assertEquals(listOf("b"), state.connections.map { it.connectionId })
            assertNull(viewModel.covers.value.pendingRemoval)

            advanceUntilIdle()
            assertEquals(listOf("a" to false), service.removeCalls)
            // A success needs no reconcile: the optimistic list is already right.
            assertEquals(1, service.listCount)
        }

    @Test
    fun `removing with a block passes the flag through`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            viewModel.requestRemoval(connection("a"))
            viewModel.confirmRemoval(block = true)
            advanceUntilIdle()

            assertEquals(listOf("a" to true), service.removeCalls)
        }

    /** A failure puts the row back by reloading the server's answer. */
    @Test
    fun `a failed removal reports and reconciles`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.requestRemoval(connection("a"))
            viewModel.confirmRemoval(block = false)
            advanceUntilIdle()

            assertEquals(R.string.error_offline, viewModel.covers.value.removeErrorRes)
            assertEquals(2, service.listCount)
            val state = viewModel.uiState.value as ConnectionsUiState.Content
            assertEquals(listOf("a"), state.connections.map { it.connectionId })

            viewModel.dismissRemoveError()
            assertNull(viewModel.covers.value.removeErrorRes)
        }

    /** The server's own condition slug picks the copy, not a substring scan. */
    @Test
    fun `a named server condition maps to its own message`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            service.failNext = postgrestException("not_authenticated")
            viewModel.requestRemoval(connection("a"))
            viewModel.confirmRemoval(block = false)
            advanceUntilIdle()

            assertEquals(R.string.connections_error_session, viewModel.covers.value.removeErrorRes)
        }

    /**
     * **The regression this ViewModel exists for.** The reconcile ran in
     * `rememberCoroutineScope`, so leaving the screen cancelled it after the RPC
     * had already severed the connection for both sides — a failure then kept a
     * dead connection on screen with no error. Clearing the `ViewModelStore`
     * cancels `viewModelScope` the way leaving does.
     */
    @Test
    fun `clearing the ViewModel mid-removal still finishes the request`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[ConnectionsViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            service.writeGate = gate
            viewModel.requestRemoval(connection("a"))
            viewModel.confirmRemoval(block = true)
            advanceUntilIdle()

            assertEquals(listOf("a" to true), service.removeCalls)

            store.clear()
            service.failNext = IOException("offline")
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the failure must still be reported",
                R.string.error_offline,
                viewModel.covers.value.removeErrorRes,
            )
            // The assertion that matters, and the one this test used to miss:
            // the error flag is written BEFORE the reconcile, so checking it
            // alone passes even when the reconcile is cancelled.
            assertEquals(
                "the reconcile must run too, or the optimistic removal is never undone",
                2,
                service.listCount,
            )
            val state = viewModel.uiState.value as ConnectionsUiState.Content
            assertEquals(listOf("a"), state.connections.map { it.connectionId })
        }

    @Test
    fun `cancelRemoval drops the target without calling the server`() =
        runTest {
            val service = FakeConnectionsService(connections = listOf(connection("a")))
            val viewModel = viewModel(service)
            advanceUntilIdle()

            viewModel.requestRemoval(connection("a"))
            viewModel.cancelRemoval()
            viewModel.confirmRemoval(block = false)
            advanceUntilIdle()

            assertTrue(service.removeCalls.isEmpty())
            val state = viewModel.uiState.value as ConnectionsUiState.Content
            assertEquals(listOf("a"), state.connections.map { it.connectionId })
        }
}
