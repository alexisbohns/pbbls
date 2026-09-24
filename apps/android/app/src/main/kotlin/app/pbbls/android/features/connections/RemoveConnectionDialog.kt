package app.pbbls.android.features.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * Remove-a-connection confirmation (M49) — the iOS `confirmationDialog`
 * analog, which offers Remove and Remove-and-block side by side.
 *
 * Deliberately not `ConfirmDeleteDialog`: that shape carries exactly one
 * destructive action, and blocking is a distinct decision with a different
 * consequence (it also stops the peer re-entering through a live invite,
 * design D6), so it must be its own explicit choice rather than a checkbox
 * or a hidden default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RemoveConnectionDialog(
    peerName: String,
    onRemove: () -> Unit,
    onRemoveAndBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.connections_remove_title, peerName),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.connections_remove_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.connections_remove_confirm),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error,
                    )
                }
                TextButton(onClick = onRemoveAndBlock) {
                    Text(
                        text = stringResource(R.string.connections_remove_block_confirm),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}
