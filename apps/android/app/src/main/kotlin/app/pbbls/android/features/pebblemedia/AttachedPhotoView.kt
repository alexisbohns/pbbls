package app.pbbls.android.features.pebblemedia

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.pebblemedia.models.AttachedSnap
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesSuccess
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Inline photo chip shown in the form's Photo section once the user has
 * picked an image — ports iOS `AttachedPhotoView.swift`. Stateless: takes the
 * current snap plus explicit [onRetry]/[onRemove]; the parent (which owns the
 * `SnapUploadCoordinator`) decides what those mean. The thumbnail decodes
 * from [AttachedSnap.localThumb] so no Storage round-trip is needed.
 */
@Composable
fun AttachedPhotoView(
    snap: AttachedSnap,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val thumb: ImageBitmap? =
        remember(snap.localThumb) {
            snap.localThumb
                .takeIf { it.isNotEmpty() }
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.asImageBitmap()
        }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumb(thumb)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PebblesText(
                text = stringResource(R.string.photo_label),
                style = PebblesTypography.subhead,
                color = system.foreground,
            )
            when (snap.state) {
                AttachedSnap.UploadState.UPLOADING ->
                    MediaStateLabel(
                        iconRes = R.drawable.ic_arrow_up_circle,
                        text = stringResource(R.string.photo_state_uploading),
                        color = system.secondary,
                    )
                AttachedSnap.UploadState.UPLOADED ->
                    MediaStateLabel(
                        iconRes = R.drawable.ic_check_circle,
                        text = stringResource(R.string.photo_state_ready),
                        color = PebblesSuccess,
                    )
                AttachedSnap.UploadState.FAILED ->
                    MediaStateLabel(
                        iconRes = R.drawable.ic_warning,
                        text = stringResource(R.string.photo_state_failed),
                        color = PebblesDestructive,
                    )
            }
        }
        Spacer(Modifier.weight(1f))
        when (snap.state) {
            AttachedSnap.UploadState.UPLOADING ->
                CircularProgressIndicator(
                    color = PebblesTheme.colors.accent.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            AttachedSnap.UploadState.UPLOADED ->
                RemovePhotoButton(onRemove)
            AttachedSnap.UploadState.FAILED ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.photo_retry_a11y),
                            tint = PebblesTheme.colors.accent.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    RemovePhotoButton(onRemove)
                }
        }
    }
}

/** 56dp rounded thumbnail with the shared placeholder — used by both media rows. */
@Composable
internal fun MediaThumb(thumb: ImageBitmap?) {
    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        MediaThumbPlaceholder()
    }
}

@Composable
internal fun MediaThumbPlaceholder() {
    val system = PebblesTheme.colors.system
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(system.secondary.copy(alpha = 0.2f)),
    )
}

/** Icon + caption state line (the iOS `Label` analog). */
@Composable
internal fun MediaStateLabel(
    iconRes: Int,
    text: String,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        PebblesText(
            text = text,
            style = PebblesTypography.captionEmphasized,
            color = color,
        )
    }
}

/** Destructive remove affordance shared by the pending and existing rows. */
@Composable
internal fun RemovePhotoButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(
            painter = painterResource(R.drawable.ic_x_circle),
            contentDescription = stringResource(R.string.photo_remove_a11y),
            tint = PebblesTheme.colors.system.secondary,
            modifier = Modifier.size(22.dp),
        )
    }
}
