package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PbblsGlyphSize
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap

/**
 * Step 0, the picture the flow starts from (D3; iOS `RecordPhotoStep`). Play's photo policy keeps a photo
 * grid out of the app, so the library opens as the system's modal picker from one calm tile that fills
 * the height where iOS draws the grid; the controls above it are [RecordPhotoControls].
 *
 * Three states, one tile that always says what it does: no photo (the disc and "Library"); picking or
 * uploading, which covers the beat (the photo, if any, under a scrim and a spinner, with the uploading
 * state for TalkBack); and a photo attached and settled (a return through Change photo: the photo under the
 * scrim with the same disc and word). A failed upload is shown on the page, not here.
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
    val busyState = stringResource(R.string.capture_photo_uploading)

    Surface(
        onClick = onPick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        // `surfaceContainer`: in dark, `surfaceContainerLow` barely separates from the page.
        color = colors.surfaceContainer,
        // One target with one name, whatever it shows; busy says so.
        modifier =
            modifier.fillMaxSize().semantics {
                contentDescription = name
                if (isBusy) stateDescription = busyState
            },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = SCRIM_ALPHA)))
            }
            when {
                isBusy -> LoadingIndicator(modifier = Modifier.size(48.dp))
                else -> LibraryInvitation(overPhoto = bitmap != null, name = name)
            }
        }
    }
}

/** The disc and the word: what a tap does, on the empty tile and over the current photo alike. */
@Composable
private fun LibraryInvitation(
    overPhoto: Boolean,
    name: String,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp).background(colors.primaryContainer, CircleShape),
        ) {
            PbblsGlyphImage(
                glyph = PbblsGlyph.CAMERA_FOCUS,
                size = PbblsGlyphSize.HERO,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
        }
        // Over a photo the word sits on a quiet pill, so it reads on any picture and in both modes.
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            modifier =
                if (overPhoto) {
                    Modifier
                        .background(
                            colors.surface.copy(alpha = PILL_ALPHA),
                            CircleShape,
                        ).padding(horizontal = Spacing.md, vertical = Spacing.xs)
                } else {
                    Modifier
                },
        )
    }
}

private const val PILL_ALPHA = 0.88f

/** Enough to read the spinner on any photo, little enough that the photo is still the picture. */
private const val SCRIM_ALPHA = 0.32f
