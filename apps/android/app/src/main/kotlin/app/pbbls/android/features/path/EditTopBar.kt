package app.pbbls.android.features.path

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/** The Cancel / [title] / Save bar shared by Edit moment and Edit pebble (R5). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditTopBar(
    title: String,
    isSaving: Boolean,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (isSaving) {
            Box(Modifier.size(48.dp), Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            // No explicit colour: TextButton's content colour is primary, and dims when disabled.
            TextButton(onClick = onSave, enabled = saveEnabled) {
                Text(
                    stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
