package app.pbbls.android.features.home.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.Spacing

/**
 * The camera moment's lines (iOS `HomeContent`): "Saving your photo…" while it saves, then a failure
 * with a close button. Draws nothing, padding included, when neither applies.
 */
@Composable
fun CameraMomentStatus(
    state: CameraMomentUiState,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isSaving && state.errorRes == null) return
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (state.isSaving) {
            Text(
                text = stringResource(R.string.home_camera_saving),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = colors.onSurfaceVariant,
            )
        }
        state.errorRes?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissError) {
                    Icon(
                        painter = painterResource(R.drawable.ic_x_circle),
                        contentDescription = stringResource(R.string.action_close),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "No camera yet" (iOS `home.camera.unavailable`): no camera, or no app answers the capture intent. */
@Composable
fun CameraUnavailableDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = { Text(text = stringResource(R.string.home_camera_unavailable), color = colors.onSurface) },
        text = { Text(text = stringResource(R.string.home_camera_unavailable_body), color = colors.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.ok), color = colors.primary) }
        },
    )
}
