package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap

/**
 * Step 0, the picture the flow starts from (D3; iOS `RecordPhotoStep`). Play's photo policy keeps a photo
 * grid out of the app, so the library opens as the system's modal picker from one calm tile that fills
 * the height where iOS draws the grid; the controls above it are [RecordPhotoControls].
 *
 * A pick advances on its own (the screen's beat), so the tile only has to say what it is doing meanwhile:
 * with a photo attached (a first pick during the beat, or a return through Change photo) it shows that
 * photo, with a spinner while it is being read or uploaded. A failed upload is shown on the page, not here.
 *
 * Stateless: the screen owns the picker, the camera and the upload.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordPhotoStep(
    snap: FormSnap?,
    isPicking: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Decoded once per byte array: the thumb is a 420px JPEG the pipeline already produced. A restored
    // photo has no bytes, so it draws as the plain tile.
    val thumb = (snap as? FormSnap.Pending)?.snap?.localThumb
    val bitmap =
        remember(thumb) {
            thumb?.takeIf { it.isNotEmpty() }?.let {
                runCatching {
                    android.graphics.BitmapFactory
                        .decodeByteArray(it, 0, it.size)
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    val isBusy = isPicking || (snap as? FormSnap.Pending)?.snap?.state == AttachedSnap.UploadState.UPLOADING
    val name = stringResource(R.string.capture_library)

    Surface(
        onClick = onPick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        // One target with one name, whatever it shows.
        modifier = modifier.fillMaxSize().semantics { contentDescription = name },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(96.dp).background(colors.primaryContainer, CircleShape),
                    ) {
                        PbblsIconImage(
                            icon = PbblsIcon.CAMERA_FOCUS,
                            size = PbblsIconSize.HERO,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                }
            }
            if (isBusy) {
                if (bitmap != null) Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = SCRIM_ALPHA)))
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}

/** Enough to read the spinner on any photo, little enough that the photo is still the picture. */
private const val SCRIM_ALPHA = 0.32f
