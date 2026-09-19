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
 * [connectionsErrorMessage] over the decoded [DataError] (#850).
 *
 * The slugs are a wire contract, so this pins them — but against the value
 * PostgREST actually sends (the bare condition, per
 * `PostgrestImpl.parseErrorResponse`), not the decorated text the substring
 * version used to be fed.
 */
class ConnectionsErrorMessageTest {
    private fun conflict(code: String) = connectionsErrorMessage(DataError.Conflict(code))

    @Test
    fun `maps the own-invite rejection`() {
        assertEquals(R.string.connections_error_own_invite, conflict("cannot_accept_own_invite"))
    }

    @Test
    fun `maps expired and not-found to the same unusable copy`() {
        // A block in either direction also arrives as invite_expired — the
        // accept path never confirms a block.
        assertEquals(R.string.connections_error_invite_unusable, conflict("invite_expired"))
        assertEquals(R.string.connections_error_invite_unusable, conflict("invite_not_found"))
    }

    @Test
    fun `maps the auth slug`() {
        assertEquals(R.string.connections_error_session, conflict("not_authenticated"))
    }

    @Test
    fun `an unrecognised condition falls back to the generic copy`() {
        assertEquals(R.string.connections_error_generic, conflict("some_new_condition"))
    }

    /**
     * A 401/403 is a dead session whatever the body says, so it earns the
     * session copy rather than the generic one — which the substring version
     * could not tell apart.
     */
    @Test
    fun `an unauthorized response reads as a session problem`() {
        assertEquals(R.string.connections_error_session, connectionsErrorMessage(DataError.Unauthorized))
    }

    @Test
    fun `offline gets its own copy`() {
        assertEquals(R.string.error_offline, connectionsErrorMessage(DataError.Network))
    }
}
