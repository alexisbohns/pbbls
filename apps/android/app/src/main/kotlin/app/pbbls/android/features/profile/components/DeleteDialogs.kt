package app.pbbls.android.features.profile.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Destructive-action confirmation for the profile surfaces — the same chrome
 * as PathScreen's pebble-delete dialog (M39 D8 idiom) with the title/message
 * parameterized, because souls and pebbles carry different consequences
 * ("linked pebbles stay" vs "can't be undone").
 */
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        title = {
            PebblesText(
                text = title,
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        text = {
            PebblesText(
                text = message,
                style = PebblesTypography.body,
                color = system.secondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                PebblesText(
                    text = stringResource(R.string.pebble_delete),
                    style = PebblesTypography.buttonLabel,
                    color = PebblesDestructive,
                )
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

/** Delete-failure notice — mirrors PathScreen's single-action error dialog. */
@Composable
internal fun DeleteErrorDialog(onDismiss: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        text = {
            PebblesText(
                text = stringResource(R.string.pebble_delete_error),
                style = PebblesTypography.body,
                color = system.secondary,
            )
        },
        confirmButton = {
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
