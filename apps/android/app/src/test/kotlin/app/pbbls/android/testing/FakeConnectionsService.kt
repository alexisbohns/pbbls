package app.pbbls.android.testing

import app.pbbls.android.services.AcceptInviteResult
import app.pbbls.android.services.Connection
import app.pbbls.android.services.ConnectionInvite
import app.pbbls.android.services.ConnectionPeer
import app.pbbls.android.services.ConnectionsServicing
import app.pbbls.android.services.InvitePreview
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ConnectionsServicing] (#849) — the connections list, the invite
 * and the accept surface, drivable without a live project. See
 * [PebblesTestHarness] for where these live and why.
 */
class FakeConnectionsService(
    /** What [list] returns on a successful call. Settable mid-test. */
    var connections: List<Connection> = emptyList(),
    /** What [createInvite] returns. */
    var invite: ConnectionInvite = ConnectionInvite(token = "tok-1", expiresAt = "2026-12-31T00:00:00Z"),
    /** What [preview] returns — `status` is the whole contract; it never raises. */
    var previewResult: InvitePreview = InvitePreview(status = "valid", inviter = ConnectionPeer(displayName = "Mo")),
    /** What [accept] returns. */
    var acceptResult: AcceptInviteResult =
        AcceptInviteResult(connectionId = "conn-1", peer = ConnectionPeer(displayName = "Mo")),
) : ConnectionsServicing {
    var listCount = 0
        private set

    /** Every `rotate` flag passed to [createInvite], oldest first. */
    val createInviteCalls = mutableListOf<Boolean>()

    /** Every token passed to [preview], oldest first. */
    val previewCalls = mutableListOf<String>()

    /** Every token passed to [accept], oldest first. */
    val acceptCalls = mutableListOf<String>()

    /** `(connectionId, block)` of every [remove], oldest first. */
    val removeCalls = mutableListOf<Pair<String, Boolean>>()

    /**
     * Awaited by [createInvite] when rotating, by [accept] and by [remove], so a
     * test can hold a write open at the server call and act while it is in
     * flight — the shape of the cancellation #849 is about.
     */
    var writeGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun list(): List<Connection> {
        listCount += 1
        armed.fire()
        return connections
    }

    override suspend fun createInvite(rotate: Boolean): ConnectionInvite {
        createInviteCalls += rotate
        if (rotate) writeGate?.await()
        armed.fire()
        return invite
    }

    override suspend fun preview(token: String): InvitePreview {
        previewCalls += token
        armed.fire()
        return previewResult
    }

    override suspend fun accept(token: String): AcceptInviteResult {
        acceptCalls += token
        writeGate?.await()
        armed.fire()
        return acceptResult
    }

    override suspend fun remove(
        connectionId: String,
        block: Boolean,
    ) {
        removeCalls += connectionId to block
        writeGate?.await()
        armed.fire()
    }
}
