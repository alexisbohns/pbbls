package app.pbbls.android.services

import app.pbbls.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `parseInviteToken` is the only entry point for an App Link token (M49,
 * design D11) — a loose parser would hand arbitrary strings to the accept RPC.
 * Pure JVM: no Android resource system involved beyond the `R` int constants.
 */
class InviteLinkTest {
    private val token = "abcDEF123-_xyz"

    @Test
    fun `accepts the canonical invite url`() {
        assertEquals(token, parseInviteToken("https://www.pbbls.app/invite/$token"))
    }

    @Test
    fun `composes the canonical invite url`() {
        assertEquals("https://www.pbbls.app/invite/$token", inviteUrl(token))
    }

    @Test
    fun `round-trips compose and parse`() {
        assertEquals(token, parseInviteToken(inviteUrl(token)))
    }

    @Test
    fun `rejects a foreign host`() {
        assertNull(parseInviteToken("https://evil.example.com/invite/$token"))
    }

    @Test
    fun `rejects plain http`() {
        assertNull(parseInviteToken("http://www.pbbls.app/invite/$token"))
    }

    @Test
    fun `rejects the oauth custom scheme`() {
        assertNull(parseInviteToken("pebbles://auth-callback"))
    }

    @Test
    fun `rejects null and blank`() {
        assertNull(parseInviteToken(null))
        assertNull(parseInviteToken(""))
    }

    @Test
    fun `rejects other paths on the same host`() {
        listOf("/docs/terms", "/invite", "/invite/", "/connections/$token").forEach { path ->
            assertNull("expected null for $path", parseInviteToken("https://www.pbbls.app$path"))
        }
    }

    @Test
    fun `rejects a deeper path under invite`() {
        assertNull(parseInviteToken("https://www.pbbls.app/invite/$token/extra"))
    }
}

/**
 * Mirrors the iOS `ConnectionsError.from` mapping. The SQL error slugs are a
 * wire contract, so this pins them.
 */
class ConnectionsErrorMessageTest {
    @Test
    fun `maps own-invite before the expiry slugs`() {
        assertEquals(
            R.string.connections_error_own_invite,
            connectionsErrorMessage("ERROR: cannot_accept_own_invite"),
        )
    }

    @Test
    fun `maps expired and not-found to the same unusable copy`() {
        // A block in either direction also arrives as invite_expired — the
        // accept path never confirms a block.
        assertEquals(
            R.string.connections_error_invite_unusable,
            connectionsErrorMessage("invite_expired"),
        )
        assertEquals(
            R.string.connections_error_invite_unusable,
            connectionsErrorMessage("invite_not_found"),
        )
    }

    @Test
    fun `maps the auth slug`() {
        assertEquals(R.string.connections_error_session, connectionsErrorMessage("not_authenticated"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals(R.string.connections_error_invite_unusable, connectionsErrorMessage("INVITE_EXPIRED"))
    }

    @Test
    fun `falls back for null and unknown text`() {
        assertEquals(R.string.connections_error_generic, connectionsErrorMessage(null))
        assertEquals(R.string.connections_error_generic, connectionsErrorMessage("some other failure"))
    }
}
