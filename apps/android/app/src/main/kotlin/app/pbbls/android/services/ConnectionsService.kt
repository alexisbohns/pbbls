package app.pbbls.android.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.GlyphStroke
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mutual connections (M49) — ports iOS `ConnectionsService`.
 *
 * Every write goes through a `security definer` RPC: the three tables carry
 * SELECT-only RLS, so there is no client write path by design. All five RPCs
 * return scalar `jsonb`, so responses use `decodeAs()` (never a single-row
 * accessor). Errors propagate to the caller, which owns loading/error view
 * state; [connectionsErrorMessage] maps the Postgres slugs to copy.
 */
class ConnectionsService(
    private val supabase: SupabaseService,
) {
    /**
     * Token lifted from an invite App Link. Compose-observable so `RootScreen`
     * reacts both to a cold start (token parked before the session resolves)
     * and to `onNewIntent` on an already-running, already-signed-in app.
     */
    var pendingInviteToken: String? by mutableStateOf(null)

    /** `get_connections() returns jsonb` — the caller's connections, newest first. */
    suspend fun list(): List<Connection> =
        supabase.client.postgrest
            .rpc("get_connections")
            .decodeAs()

    /**
     * Returns the caller's live invite, minting one only when none exists.
     * [rotate] revokes the live invite and issues a fresh token — the whole
     * revocation surface, so a link already shared stays alive until then.
     */
    suspend fun createInvite(rotate: Boolean = false): ConnectionInvite =
        supabase.client.postgrest
            .rpc(
                "create_connection_invite",
                buildJsonObject { put("p_rotate", rotate) },
            ).decodeAs()

    /**
     * Anon-callable preview: who is inviting, before accepting. Never raises —
     * an unusable token comes back as a status, not an error.
     */
    suspend fun preview(token: String): InvitePreview =
        supabase.client.postgrest
            .rpc(
                "preview_connection_invite",
                buildJsonObject { put("p_token", token) },
            ).decodeAs()

    /**
     * Accepting is the mutual consent. A repeat accept SUCCEEDS with
     * `alreadyConnected` — re-scanning a shared QR is the normal case, not an
     * error. A block in either direction surfaces as `invite_expired`.
     */
    suspend fun accept(token: String): AcceptInviteResult =
        supabase.client.postgrest
            .rpc(
                "accept_connection_invite",
                buildJsonObject { put("p_token", token) },
            ).decodeAs()

    /**
     * Severs the connection for both sides. [block] additionally records a
     * one-way block that stops the peer re-entering through a live invite.
     */
    suspend fun remove(
        connectionId: String,
        block: Boolean = false,
    ) {
        supabase.client.postgrest.rpc(
            "remove_connection",
            buildJsonObject {
                put("p_connection_id", connectionId)
                put("p_block", block)
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Wire models. UUIDs are Strings on Android; timestamps stay Strings because
// the RPC projections are rendered relatively, never parsed for arithmetic.
// ---------------------------------------------------------------------------

/** The peer's avatar geometry — the render payload only, no id or ownership. */
@Serializable
data class PeerGlyph(
    val strokes: List<GlyphStroke> = emptyList(),
    @SerialName("view_box") val viewBox: String,
)

/** A peer as projected by the connection RPCs — never a `profiles` row. */
@Serializable
data class ConnectionPeer(
    @SerialName("display_name") val displayName: String? = null,
    val glyph: PeerGlyph? = null,
)

/** One row of `get_connections()`. */
@Serializable
data class Connection(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("connected_at") val connectedAt: String,
    val peer: ConnectionPeer,
)

/** `create_connection_invite()` — the live invite, re-displayed on every open. */
@Serializable
data class ConnectionInvite(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
) {
    /**
     * The one canonical invite URL. No custom scheme: this https link opens
     * the app through App Links when installed, and the web accept page when
     * not (design D11).
     */
    val url: String get() = inviteUrl(token)
}

/** `preview_connection_invite()` — never an error, always a status. */
@Serializable
data class InvitePreview(
    val status: String,
    /** Present only for `valid`: a withdrawn or expired token goes fully dark. */
    val inviter: ConnectionPeer? = null,
) {
    val isValid: Boolean get() = status == "valid"
}

/** `accept_connection_invite()`. `alreadyConnected` is a success state. */
@Serializable
data class AcceptInviteResult(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("already_connected") val alreadyConnected: Boolean = false,
    val peer: ConnectionPeer,
)

/** Composes the canonical invite URL for [token]. Pure; JVM-tested. */
fun inviteUrl(token: String): String = "https://$INVITE_HOST/invite/$token"

/**
 * Extracts the token from an invite App Link, accepting exactly the canonical
 * URL shape. A loose parser would hand arbitrary strings to the accept RPC.
 * Pure; JVM-tested.
 */
fun parseInviteToken(url: String?): String? {
    val uri = runCatching { java.net.URI(url ?: return null) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != INVITE_HOST) return null
    val segments = uri.path.orEmpty().split('/')
    val parts = segments.filter { it.isNotEmpty() }
    if (parts.size != 2 || parts[0] != "invite") return null
    return parts[1].ifEmpty { null }
}

const val INVITE_HOST = "www.pbbls.app"

/**
 * iOS `ConnectionsError.from` — substring-contains on the lowercased error
 * text, in the iOS order, mapped to the localized catalog (pure; JVM-tested).
 */
fun connectionsErrorMessage(message: String?): Int {
    val lowered = message?.lowercase() ?: return R.string.connections_error_generic
    return when {
        "cannot_accept_own_invite" in lowered -> R.string.connections_error_own_invite
        // `invite_expired` also covers revoked tokens and a block in either
        // direction — deliberately indistinguishable, so a block is never
        // confirmed by the accept path.
        "invite_expired" in lowered -> R.string.connections_error_invite_unusable
        "invite_not_found" in lowered -> R.string.connections_error_invite_unusable
        "not_authenticated" in lowered -> R.string.connections_error_session
        else -> R.string.connections_error_generic
    }
}

/** CompositionLocal for [ConnectionsService]. */
val LocalConnectionsService =
    staticCompositionLocalOf<ConnectionsService> {
        error("LocalConnectionsService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
