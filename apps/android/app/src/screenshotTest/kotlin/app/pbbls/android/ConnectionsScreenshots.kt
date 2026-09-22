package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.AcceptInviteResult
import app.pbbls.android.core.data.Connection
import app.pbbls.android.core.data.ConnectionInvite
import app.pbbls.android.core.data.ConnectionPeer
import app.pbbls.android.core.data.InvitePreview
import app.pbbls.android.core.data.PeerGlyph
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.features.connections.AcceptInviteContent
import app.pbbls.android.features.connections.AcceptInviteUiState
import app.pbbls.android.features.connections.ConnectionsContent
import app.pbbls.android.features.connections.ConnectionsUiState
import app.pbbls.android.features.connections.InviteContent
import app.pbbls.android.features.connections.InviteUiState
import com.android.tools.screenshot.PreviewTest

/**
 * Render-to-view previews for the M49 connections surfaces — the SDK-less
 * maintainer reviews these as the `ui-screenshots` CI artifact. Drives the
 * stateless `*Content` layers, never the service-reading screens.
 *
 * Since #849 each one takes its screen's sealed `UiState` rather than a bag of
 * nullables and flags, so a preview now names the case it renders instead of
 * spelling it out as "not loading, did not fail, these rows". No reference PNG
 * moves: the rendered trees are identical.
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
@PreviewLargeFont
@Composable
private fun ConnectionsListPreview() {
    PebblesTheme {
        ConnectionsContent(
            uiState = ConnectionsUiState.Content(sampleConnections),
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
            uiState = ConnectionsUiState.Content(emptyList()),
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
            uiState = ConnectionsUiState.Content(sampleConnections),
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
            uiState = InviteUiState.Content(sampleInvite),
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
            uiState =
                AcceptInviteUiState.Ready(
                    InvitePreview(status = "valid", inviter = peer("Lea")),
                ),
            onAccept = {},
            onRetry = {},
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
            // A repeat accept is a success state, never an error.
            uiState =
                AcceptInviteUiState.Accepted(
                    AcceptInviteResult("c1", alreadyConnected = true, peer = peer("Lea")),
                ),
            onAccept = {},
            onRetry = {},
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
            uiState = AcceptInviteUiState.Ready(InvitePreview(status = "expired", inviter = null)),
            onAccept = {},
            onRetry = {},
            onDismiss = {},
        )
    }
}
