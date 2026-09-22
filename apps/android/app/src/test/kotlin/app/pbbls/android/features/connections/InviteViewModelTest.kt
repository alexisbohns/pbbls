package app.pbbls.android.features.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.core.data.ConnectionInvite
import app.pbbls.android.testing.FakeConnectionsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The invite cover's load, rotation and reset contract (#849). */
class InviteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun invite(token: String) = ConnectionInvite(token = token, expiresAt = "2026-12-31T00:00:00Z")

    private fun viewModel(service: FakeConnectionsService = FakeConnectionsService()) = InviteViewModel(service)

    @Test
    fun `starts Loading and resolves to the live invite`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)

            viewModel.start()
            assertEquals(InviteUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as InviteUiState.Content
            assertEquals("tok-1", state.invite.token)
            // The first read must not rotate: a link already shared stays alive.
            assertEquals(listOf(false), service.createInviteCalls)
        }

    /**
     * The rotation guard. Without it the screen's `LaunchedEffect` re-running
     * after a configuration change would flash the spinner over a QR the user
     * may be mid-scan on.
     */
    @Test
    fun `start is idempotent`() =
        runTest {
            val service = FakeConnectionsService()
            val viewModel = viewModel(service)

            viewModel.start()
            advanceUntilIdle()
            repeat(3) { viewModel.start() }
            advanceUntilIdle()

            assertEquals(1, service.createInviteCalls.size)
        }

    @Test
    fun `a failed load becomes Error, and retry recovers`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            service.failNext = IOException("offline")
            val viewModel = viewModel(service)

            viewModel.start()
            advanceUntilIdle()
            assertEquals(
                R.string.error_offline,
                (viewModel.uiState.value as InviteUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is InviteUiState.Content)
        }

    // MARK: - Rotation

    @Test
    fun `rotate revokes the old token and shows the new one`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)
            viewModel.start()
            advanceUntilIdle()

            service.invite = invite("tok-2")
            viewModel.rotate()
            assertTrue((viewModel.uiState.value as InviteUiState.Content).isRotating)

            advanceUntilIdle()
            val state = viewModel.uiState.value as InviteUiState.Content
            assertEquals("tok-2", state.invite.token)
            assertFalse(state.isRotating)
            assertEquals(listOf(false, true), service.createInviteCalls)
        }

    /**
     * A failed rotation keeps the live invite up: the server still has the old
     * token, so dropping to the error state would hide a link that works.
     */
    @Test
    fun `a failed rotation keeps the invite and shows the reason`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)
            viewModel.start()
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.rotate()
            advanceUntilIdle()

            val state = viewModel.uiState.value as InviteUiState.Content
            assertEquals("tok-1", state.invite.token)
            assertFalse(state.isRotating)
            assertEquals(R.string.error_offline, state.rotateErrorRes)
        }

    /** The next rotation clears the previous failure rather than stacking on it. */
    @Test
    fun `a later rotation clears the previous failure`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)
            viewModel.start()
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.rotate()
            advanceUntilIdle()
            assertEquals(R.string.error_offline, (viewModel.uiState.value as InviteUiState.Content).rotateErrorRes)

            service.invite = invite("tok-2")
            viewModel.rotate()
            advanceUntilIdle()

            val state = viewModel.uiState.value as InviteUiState.Content
            assertEquals("tok-2", state.invite.token)
            assertNull(state.rotateErrorRes)
        }

    @Test
    fun `rotate is refused while a rotation is in flight`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)
            viewModel.start()
            advanceUntilIdle()

            service.writeGate = CompletableDeferred()
            viewModel.rotate()
            advanceUntilIdle()
            viewModel.rotate()
            advanceUntilIdle()

            assertEquals(listOf(false, true), service.createInviteCalls)
            service.writeGate?.complete(Unit)
            advanceUntilIdle()
        }

    /**
     * **The regression this ViewModel exists for, and the sharpest one in this
     * part.** Rotating revokes the live token server-side. `onRotate` ran in
     * `rememberCoroutineScope`, so dismissing the cover between the RPC landing
     * and the assignment cancelled it *after* the old token was revoked — the
     * screen and the user's clipboard kept a link that no longer worked, with no
     * error and no sign anything had happened.
     */
    @Test
    fun `clearing the ViewModel mid-rotation still lands the new token`() =
        runTest {
            val service = FakeConnectionsService(invite = invite("tok-1"))
            val viewModel = viewModel(service)
            viewModel.start()
            advanceUntilIdle()

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[InviteViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            service.writeGate = gate
            service.invite = invite("tok-2")
            viewModel.rotate()
            advanceUntilIdle()

            // The revocation has left the device.
            assertEquals(listOf(false, true), service.createInviteCalls)
            assertEquals("tok-1", (viewModel.uiState.value as InviteUiState.Content).invite.token)

            // The user dismisses the cover.
            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the new token must still land, or the screen keeps a revoked link",
                "tok-2",
                (viewModel.uiState.value as InviteUiState.Content).invite.token,
            )
        }
}
