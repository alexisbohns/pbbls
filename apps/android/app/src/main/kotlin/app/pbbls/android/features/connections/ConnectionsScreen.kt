package app.pbbls.android.features.connections

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.services.Connection
import app.pbbls.android.services.LocalConnectionsService
import app.pbbls.android.services.connectionsErrorMessage
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "connections"

/**
 * The people you're connected with (M49) — ports iOS `ConnectionsListView`.
 *
 * Discovery is invite-only by design: no search, no directory (design §1).
 * Removing severs for both sides; removing with a block also stops the peer
 * re-entering through a live invite.
 *
 * Self-applies `safeDrawingPadding()`, so the caller composes it in the OUTER
 * (unpadded) Box alongside the other full-screen covers.
 */
@Composable
fun ConnectionsScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = LocalConnectionsService.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<Connection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var pendingRemoval by remember { mutableStateOf<Connection?>(null) }
    var removeErrorRes by remember { mutableStateOf<Int?>(null) }
    var isPresentingInvite by remember { mutableStateOf(false) }

    LaunchedEffect(retryKey) {
        isLoading = true
        loadFailed = false
        try {
            rows = service.list()
            isLoading = false
        } catch (e: Exception) {
            Log.e(TAG, "connections load failed", e)
            loadFailed = true
            isLoading = false
        }
    }

    BackHandler { onDismiss() }

    // Optimistic removal: the row leaves immediately and a failure reloads the
    // truth back in.
    fun remove(
        row: Connection,
        block: Boolean,
    ) {
        pendingRemoval = null
        rows = rows.filterNot { it.connectionId == row.connectionId }
        scope.launch {
            try {
                service.remove(connectionId = row.connectionId, block = block)
            } catch (e: Exception) {
                Log.e(TAG, "connection removal failed", e)
                removeErrorRes = connectionsErrorMessage(e.message)
                retryKey++
            }
        }
    }

    ConnectionsContent(
        connections = rows,
        isLoading = isLoading,
        loadFailed = loadFailed,
        onRetry = { retryKey++ },
        onRemoveRequest = { pendingRemoval = it },
        onOpenInvite = { isPresentingInvite = true },
        onDismiss = onDismiss,
        modifier = modifier,
    )

    pendingRemoval?.let { row ->
        RemoveConnectionDialog(
            peerName = row.peer.displayName ?: stringResource(R.string.connections_unnamed_peer),
            onRemove = { remove(row, block = false) },
            onRemoveAndBlock = { remove(row, block = true) },
            onDismiss = { pendingRemoval = null },
        )
    }

    removeErrorRes?.let { res ->
        DeleteErrorDialog(
            onDismiss = { removeErrorRes = null },
            message = stringResource(res),
        )
    }

    if (isPresentingInvite) {
        InviteScreen(onDismiss = { isPresentingInvite = false })
    }
}

/**
 * Stateless connections list — what screenshot previews drive. Takes its data
 * as parameters rather than reading services.
 */
@Composable
fun ConnectionsContent(
    connections: List<Connection>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onRemoveRequest: (Connection) -> Unit,
    onOpenInvite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.connections_title),
            titleStyle = PebblesTypography.headlineEmphasized,
            titleColor = system.foreground,
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_done),
                    onClick = onDismiss,
                    color = accent.primary,
                )
            },
            trailing = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.connections_invite_action),
                    onClick = onOpenInvite,
                    color = accent.primary,
                )
            },
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = accent.primary)

                loadFailed ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PebblesText(
                            text = stringResource(R.string.connections_load_error),
                            style = PebblesTypography.body,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        TextButton(onClick = onRetry) {
                            PebblesText(
                                text = stringResource(R.string.action_retry),
                                style = PebblesTypography.buttonLabel,
                                color = accent.primary,
                            )
                        }
                    }

                connections.isEmpty() ->
                    PebblesText(
                        text = stringResource(R.string.connections_empty),
                        style = PebblesTypography.body,
                        color = system.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(connections, key = { it.connectionId }) { row ->
                            ConnectionRow(
                                connection = row,
                                onRemoveRequest = { onRemoveRequest(row) },
                            )
                        }
                    }
            }
        }
    }
}

/** One connected person: their glyph, their name, and a remove affordance. */
@Composable
private fun ConnectionRow(
    connection: Connection,
    onRemoveRequest: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            connection.peer.glyph?.let { glyph ->
                GlyphView(
                    case = GlyphViewCase.PROFILE,
                    strokes = glyph.strokes,
                    viewBox = glyph.viewBox,
                    side = 36.dp,
                )
            }
        }

        PebblesText(
            text = connection.peer.displayName ?: stringResource(R.string.connections_unnamed_peer),
            style = PebblesTypography.body,
            color = system.foreground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )

        TextButton(onClick = onRemoveRequest) {
            PebblesText(
                text = stringResource(R.string.connections_remove_action),
                style = PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}
