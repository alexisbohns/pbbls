package app.pbbls.android.features.path.record.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap

private val TILE_HEIGHT = 280.dp

/**
 * Step 0 — the picture the flow starts from — ports iOS `RecordPhotoStep`.
 *
 * Does **not** auto-advance on pick (M58 D3): the upload runs in the background
 * and its state belongs on screen while the user is still looking at the photo.
 * Picking swaps Skip for Done and waits.
 *
 * Stateless — the screen owns the picker launcher and the upload coordinator.
 */
@Composable
fun RecordPhotoStep(
    snap: FormSnap?,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (snap) {
            null -> AddTile(onPick = onPick)
            is FormSnap.Pending -> PickedPhoto(snap = snap.snap, onPick = onPick, onRetry = onRetry, onRemove = onRemove)
            // Only reachable when resuming a draft that already carries a snap;
            // the bytes live in Storage, so the step just confirms one is
            // attached rather than re-rendering it.
            is FormSnap.Existing -> AttachedWithoutThumb(onRemove = onRemove)
        }
    }
}

@Composable
private fun AddTile(onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // `outline`, not `outlineVariant`: an empty slot the user must find and tap,
    // so its boundary needs 3:1 (WCAG 1.4.11) — the DashedPlaceholder rule.
    val dashColor = colors.outline
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT)
                .drawBehind {
                    // The dashes trace the clip below, so both read the same shape.
                    val radius = shape.topStart.toPx(size, this)
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(radius),
                        style =
                            Stroke(
                                width = 2.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(10.dp.toPx(), 10.dp.toPx()),
                                    ),
                            ),
                    )
                }.clip(shape)
                .clickable(onClick = onPick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.photo_add),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickedPhoto(
    snap: AttachedSnap,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Decoded once per byte array: the thumb is a fixed 420px JPEG the pipeline
    // already produced, so there is nothing to reload or resize.
    val bitmap =
        remember(snap.localThumb) {
            runCatching {
                android.graphics.BitmapFactory
                    .decodeByteArray(snap.localThumb, 0, snap.localThumb.size)
                    ?.asImageBitmap()
            }.getOrNull()
        }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TILE_HEIGHT)
                    .clip(MaterialTheme.shapes.extraLarge),
        )
    }

    when (snap.state) {
        AttachedSnap.UploadState.UPLOADING ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.photo_state_uploading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

        AttachedSnap.UploadState.UPLOADED ->
            TextButton(onClick = onPick) {
                Text(
                    text = stringResource(R.string.record_photo_choose_another),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

        AttachedSnap.UploadState.FAILED ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.record_photo_upload_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.record_photo_retry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                        )
                    }
                    TextButton(onClick = onRemove) {
                        Text(
                            text = stringResource(R.string.record_photo_remove),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                    }
                }
            }
    }
}

@Composable
private fun AttachedWithoutThumb(onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().height(TILE_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_image),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.record_photo_attached),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.record_photo_remove),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }
    }
}
