package app.pbbls.android.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.model.MomentDeleteChoice

/**
 * The delete confirmation for a moment (R8, shared copy with iOS). Without your
 * pebble: "Delete this moment?", "This can't be undone.", Delete moment. With
 * one: "Delete this pebble?", and a choice between the pebble only (the moment
 * stays, as a blank stone) and the moment too.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MomentDeleteDialog(
    hasPebble: Boolean,
    onConfirm: (MomentDeleteChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(if (hasPebble) R.string.delete_pebble_title else R.string.delete_moment_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(if (hasPebble) R.string.delete_pebble_message else R.string.pebble_delete_confirm_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (hasPebble) {
                    TextButton(onClick = { onConfirm(MomentDeleteChoice.PEBBLE_ONLY) }) {
                        Text(
                            stringResource(R.string.delete_pebble_keep_moment),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.error,
                        )
                    }
                }
                TextButton(onClick = { onConfirm(MomentDeleteChoice.MOMENT) }) {
                    Text(
                        stringResource(if (hasPebble) R.string.delete_moment_too else R.string.delete_moment),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        },
    )
}
