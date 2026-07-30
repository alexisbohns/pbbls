package app.pbbls.android.features.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

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
@Composable
internal fun RemoveConnectionDialog(
    peerName: String,
    onRemove: () -> Unit,
    onRemoveAndBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.connections_remove_title, peerName),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        text = {
            PebblesText(
                text = stringResource(R.string.connections_remove_message),
                style = PebblesTypography.body,
                color = system.secondary,
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRemove) {
                    PebblesText(
                        text = stringResource(R.string.connections_remove_confirm),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                }
                TextButton(onClick = onRemoveAndBlock) {
                    PebblesText(
                        text = stringResource(R.string.connections_remove_block_confirm),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                PebblesText(
                    text = stringResource(R.string.action_cancel),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
    )
}
