package app.pbbls.android.features.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.services.AcceptInviteResult
import app.pbbls.android.services.ConnectionPeer
import app.pbbls.android.services.InvitePreview
import app.pbbls.android.testing.FakeConnectionsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The accept surface's preview, consent and reset contract (#849). */
class AcceptInviteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(service: FakeConnectionsService = FakeConnectionsService()) = AcceptInviteViewModel(service)

    @Test
    fun `starts Loading and resolves to the preview`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)

            viewModel.start("tok-1")
            assertEquals(AcceptInviteUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AcceptInviteUiState.Ready
            assertTrue(state.preview.isValid)
            assertEquals(listOf("tok-1"), service.previewCalls)
        }

    /** The preview must never accept on its own — consent is the explicit tap. */
    @Test
    fun `start previews but never accepts`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)

            viewModel.start("tok-1")
            advanceUntilIdle()

            assertTrue(service.acceptCalls.isEmpty())
        }

    @Test
    fun `start is idempotent for the same token`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)

            viewModel.start("tok-1")
            advanceUntilIdle()
            repeat(3) { viewModel.start("tok-1") }
            advanceUntilIdle()

            assertEquals(1, service.previewCalls.size)
        }

    /**
     * A withdrawn or expired token is a valid *answer* — the RPC never raises
     * for it — so it stays Ready with an invalid preview and the content layer
     * renders the dark copy.
     */
    @Test
    fun `an unusable token is Ready-but-invalid, not Error`() =
        runTest {
            val service = FakeConnectionsService(previewResult = InvitePreview(status = "expired"))
            val viewModel = viewModel(service)

            viewModel.start("tok-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value as AcceptInviteUiState.Ready
            assertFalse(state.preview.isValid)
        }

    /**
     * …whereas a failed *request* is Error. The two used to share a branch, so
     * being offline told the user their friend's invite was dead.
     */
    @Test
    fun `a failed preview is Error, and retry recovers`() =
        runTest {
            val service = FakeConnectionsService()
            service.failNext = IOException("offline")
            val viewModel = viewModel(service)

            viewModel.start("tok-1")
            advanceUntilIdle()
            assertEquals(
                R.string.error_offline,
                (viewModel.uiState.value as AcceptInviteUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AcceptInviteUiState.Ready)
        }

    // MARK: - Accept

    @Test
    fun `accept connects and reports the peer`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            viewModel.accept()
            assertTrue((viewModel.uiState.value as AcceptInviteUiState.Ready).isAccepting)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AcceptInviteUiState.Accepted
            assertEquals("Mo", state.result.peer.displayName)
            assertEquals(listOf("tok-1"), service.acceptCalls)
        }

    /** Re-scanning a shared QR is the normal case, not an error. */
    @Test
    fun `a repeat accept is a success state`() =
        runTest {
            val service =
                FakeConnectionsService(
                    acceptResult =
                        AcceptInviteResult(
                            connectionId = "conn-1",
                            alreadyConnected = true,
                            peer = ConnectionPeer(displayName = "Mo"),
                        ),
                )
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            viewModel.accept()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AcceptInviteUiState.Accepted
            assertTrue(state.result.alreadyConnected)
        }

    /** A failed accept keeps the prompt: the token may well still be good. */
    @Test
    fun `a failed accept keeps the prompt and shows the reason`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            service.failNext = postgrestException("cannot_accept_own_invite")
            viewModel.accept()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AcceptInviteUiState.Ready
            assertFalse(state.isAccepting)
            assertEquals(R.string.connections_error_own_invite, state.acceptErrorRes)
        }

    @Test
    fun `accept is refused while one is in flight`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            service.writeGate = CompletableDeferred()
            viewModel.accept()
            advanceUntilIdle()
            viewModel.accept()
            advanceUntilIdle()

            assertEquals(1, service.acceptCalls.size)
            service.writeGate?.complete(Unit)
            advanceUntilIdle()
        }

    /**
     * **The regression this ViewModel exists for.** The RPC consumes the token.
     * `onAccept` ran in `rememberCoroutineScope`, so leaving between the RPC
     * landing and the assignment cancelled it after the connection already
     * existed: no confirmation, a peer appearing with no explanation, and
     * re-opening the link taking the `alreadyConnected` path meant for a
     * re-scanned QR.
     */
    @Test
    fun `clearing the ViewModel mid-accept still lands the result`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[AcceptInviteViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            service.writeGate = gate
            viewModel.accept()
            advanceUntilIdle()
            assertEquals(listOf("tok-1"), service.acceptCalls)

            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(
                "the accepted result must still land, or the connection exists unannounced",
                viewModel.uiState.value is AcceptInviteUiState.Accepted,
            )
        }

    // MARK: - Reset

    /**
     * This ViewModel is activity-scoped — `RootScreen` composes its surface
     * above the nav host — so it outlives every destination. Without the reset a
     * second invite link in the same session opens onto the first one's result.
     */
    @Test
    fun `finish clears the result so the next link starts fresh`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()
            viewModel.accept()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AcceptInviteUiState.Accepted)

            viewModel.finish()
            assertEquals(AcceptInviteUiState.Loading, viewModel.uiState.value)

            viewModel.start("tok-2")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AcceptInviteUiState.Ready)
            assertEquals(listOf("tok-1", "tok-2"), service.previewCalls)
        }

    /**
     * `finish()` must fence an accept already in flight.
     *
     * The accept runs under `withContext(NonCancellable)` — which is what makes
     * it survive the scope dying, and therefore also what stops `finish()` from
     * stopping it. Without the epoch check the late success overwrites the
     * reset, and because this ViewModel is activity-scoped the *next* invite
     * link composes onto `Accepted`: at least one rendered frame says "you're
     * connected with <the previous peer>" over a Done button, and a tap landing
     * there discards the new invite unseen.
     */
    @Test
    fun `finish fences an accept still in flight`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            service.writeGate = gate
            viewModel.accept()
            advanceUntilIdle()

            // The user backs out while the request is in flight.
            viewModel.finish()
            gate.complete(Unit)
            advanceUntilIdle()

            // The request still landed — the connection is real…
            assertEquals(listOf("tok-1"), service.acceptCalls)
            // …but it must not repaint the surface it no longer belongs to.
            assertEquals(AcceptInviteUiState.Loading, viewModel.uiState.value)
        }

    /** The same fence on the failure branch, which republished a stale Ready. */
    @Test
    fun `finish fences a failing accept too`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            service.writeGate = gate
            viewModel.accept()
            advanceUntilIdle()

            viewModel.finish()
            service.failNext = IOException("offline")
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AcceptInviteUiState.Loading, viewModel.uiState.value)
        }

    /** Even the same token re-arrives clean after a finish. */
    @Test
    fun `finish allows the same token to be previewed again`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)
            viewModel.start("tok-1")
            advanceUntilIdle()

            viewModel.finish()
            viewModel.start("tok-1")
            advanceUntilIdle()

            assertEquals(listOf("tok-1", "tok-1"), service.previewCalls)
        }
}
