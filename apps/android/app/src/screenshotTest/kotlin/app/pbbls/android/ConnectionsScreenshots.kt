package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.connections.AcceptInviteContent
import app.pbbls.android.features.connections.ConnectionsContent
import app.pbbls.android.features.connections.InviteContent
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.services.AcceptInviteResult
import app.pbbls.android.services.Connection
import app.pbbls.android.services.ConnectionInvite
import app.pbbls.android.services.ConnectionPeer
import app.pbbls.android.services.InvitePreview
import app.pbbls.android.services.PeerGlyph
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Render-to-view previews for the M49 connections surfaces — the SDK-less
 * maintainer reviews these as the `ui-screenshots` CI artifact. Drives the
 * stateless `*Content` layers, never the service-reading screens.
 */
private val sampleGlyph =
    PeerGlyph(
        strokes =
            listOf(
                GlyphStroke(d = "M 40,150 L 100,50 L 160,150", width = 8.0),
                GlyphStroke(d = "M 70,110 L 130,110", width = 8.0),
            ),
        viewBox = "0 0 200 200",
    )

private fun peer(name: String?) = ConnectionPeer(displayName = name, glyph = sampleGlyph)

private val sampleConnections =
    listOf(
        Connection("c1", "2026-07-28T10:00:00+00:00", peer("Lea")),
        Connection("c2", "2026-07-24T18:30:00+00:00", peer("Marc")),
        // A peer who never set a display name falls back to the generic label.
        Connection("c3", "2026-07-20T09:15:00+00:00", ConnectionPeer(displayName = null, glyph = null)),
    )

private val sampleInvite =
    ConnectionInvite(token = "iH8sK2mQ4tZ9pL3vB6nX1cR5wY7dF0gJ2kM", expiresAt = "2026-08-06T10:00:00+00:00")

@PreviewTest
@Preview(name = "Connections · list", showBackground = true)
@Composable
private fun ConnectionsListPreview() {
    PebblesTheme {
        ConnectionsContent(
            connections = sampleConnections,
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onRemoveRequest = {},
            onOpenInvite = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Connections · empty", showBackground = true)
@Composable
private fun ConnectionsEmptyPreview() {
    PebblesTheme {
        ConnectionsContent(
            connections = emptyList(),
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onRemoveRequest = {},
            onOpenInvite = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Connections · list dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ConnectionsListDarkPreview() {
    PebblesTheme {
        ConnectionsContent(
            connections = sampleConnections,
            isLoading = false,
            loadFailed = false,
            onRetry = {},
            onRemoveRequest = {},
            onOpenInvite = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Invite · link and QR", showBackground = true)
@Composable
private fun InviteLinkPreview() {
    PebblesTheme {
        InviteContent(
            invite = sampleInvite,
            isLoading = false,
            errorRes = null,
            isRotating = false,
            onRetry = {},
            onCopy = {},
            onShare = {},
            onRotate = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Accept · valid invite", showBackground = true)
@Composable
private fun AcceptValidPreview() {
    PebblesTheme {
        AcceptInviteContent(
            preview = InvitePreview(status = "valid", inviter = peer("Lea")),
            accepted = null,
            isLoading = false,
            isAccepting = false,
            errorRes = null,
            onAccept = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Accept · already connected", showBackground = true)
@Composable
private fun AcceptAlreadyConnectedPreview() {
    PebblesTheme {
        AcceptInviteContent(
            preview = InvitePreview(status = "valid", inviter = peer("Lea")),
            // A repeat accept is a success state, never an error.
            accepted = AcceptInviteResult("c1", alreadyConnected = true, peer = peer("Lea")),
            isLoading = false,
            isAccepting = false,
            errorRes = null,
            onAccept = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Accept · unusable invite", showBackground = true)
@Composable
private fun AcceptUnusablePreview() {
    PebblesTheme {
        AcceptInviteContent(
            // Expired, revoked and blocked all render this one dark state.
            preview = InvitePreview(status = "expired", inviter = null),
            accepted = null,
            isLoading = false,
            isAccepting = false,
            errorRes = null,
            onAccept = {},
            onDismiss = {},
        )
    }
}
