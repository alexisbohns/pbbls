package app.pbbls.android.features.connections

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.Connection
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

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
    onOpenInvite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    ConnectionsContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onRemoveRequest = viewModel::requestRemoval,
        onOpenInvite = onOpenInvite,
        onDismiss = onDismiss,
        modifier = modifier,
    )

    covers.pendingRemoval?.let { row ->
        RemoveConnectionDialog(
            peerName = row.peer.displayName ?: stringResource(R.string.connections_unnamed_peer),
            onRemove = { viewModel.confirmRemoval(block = false) },
            onRemoveAndBlock = { viewModel.confirmRemoval(block = true) },
            onDismiss = viewModel::cancelRemoval,
        )
    }

    covers.removeErrorRes?.let { res ->
        DeleteErrorDialog(
            onDismiss = viewModel::dismissRemoveError,
            message = stringResource(res),
        )
    }
}

/**
 * Stateless connections list — what screenshot previews drive. Takes its data
 * as parameters rather than reading services.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionsContent(
    uiState: ConnectionsUiState,
    onRetry: () -> Unit,
    onRemoveRequest: (Connection) -> Unit,
    onOpenInvite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .readableWidth(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.connections_title),
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_done),
                    onClick = onDismiss,
                    color = colors.primary,
                )
            },
            trailing = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.connections_invite_action),
                    onClick = onOpenInvite,
                    color = colors.primary,
                )
            },
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Exhaustive with no `else`: a new ConnectionsUiState case must be
            // rendered. The empty list is Content, not a fourth case.
            when (uiState) {
                ConnectionsUiState.Loading -> LoadingIndicator()

                is ConnectionsUiState.Error ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(uiState.messageRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        TextButton(onClick = onRetry) {
                            Text(
                                text = stringResource(R.string.action_retry),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                            )
                        }
                    }

                is ConnectionsUiState.Content ->
                    if (uiState.connections.isEmpty()) {
                        Text(
                            text = stringResource(R.string.connections_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.connections, key = { it.connectionId }) { row ->
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
}

/** One connected person: their glyph, their name, and a remove affordance. */
@Composable
private fun ConnectionRow(
    connection: Connection,
    onRemoveRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

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

        Text(
            text = connection.peer.displayName ?: stringResource(R.string.connections_unnamed_peer),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )

        TextButton(onClick = onRemoveRequest) {
            Text(
                text = stringResource(R.string.connections_remove_action),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}
