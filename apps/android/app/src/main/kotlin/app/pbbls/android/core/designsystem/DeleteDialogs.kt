package app.pbbls.android.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/**
 * Destructive-action confirmation for the profile surfaces — the same chrome
 * as PathScreen's pebble-delete dialog (M39 D8 idiom) with the title/message
 * parameterized, because souls and pebbles carry different consequences
 * ("linked pebbles stay" vs "can't be undone"). `confirmText` defaults to the
 * generic Delete label; account deletion passes its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText ?: stringResource(R.string.pebble_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.error,
                )
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

/** Delete-failure notice — mirrors PathScreen's single-action error dialog. */
@Composable
internal fun DeleteErrorDialog(
    onDismiss: () -> Unit,
    message: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        text = {
            Text(
                text = message ?: stringResource(R.string.pebble_delete_error),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
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
