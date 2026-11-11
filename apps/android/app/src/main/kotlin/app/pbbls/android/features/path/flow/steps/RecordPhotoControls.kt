package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.Spacing

/**
 * The photo step's fixed-slot controls (record-flow D3; iOS `RecordPhotoControls`): the way past the
 * picture without one, and the camera, only where a camera app answers. "Just write" takes the width, so
 * the camera stays 48 dp at any font size and the label is what wraps. Owned by `CaptureFlowScreen`,
 * which advances and launches.
 */
@Composable
fun RecordPhotoControls(
    enabled: Boolean,
    showsCamera: Boolean,
    onJustWrite: () -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onJustWrite,
            enabled = enabled,
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Text(text = stringResource(R.string.capture_just_write), style = MaterialTheme.typography.labelLarge)
        }
        if (showsCamera) {
            FilledTonalIconButton(
                onClick = onCamera,
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                modifier = Modifier.size(48.dp),
            ) {
                PbblsGlyphImage(
                    glyph = PbblsGlyph.CAMERA,
                    size = PbblsGlyphSize.TOOLBAR,
                    contentDescription = stringResource(R.string.capture_take_photo_a11y),
                )
            }
        }
    }
}
