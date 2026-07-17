package app.pbbls.android.features.pebblemedia

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.theme.PebblesSuccess
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import coil3.compose.AsyncImage

private const val TAG = "existing-snap-row"

/**
 * Form row for an already-saved snap inside the edit flow's Photo section —
 * ports iOS `ExistingSnapRow.swift`. Layout matches [AttachedPhotoView]
 * (56dp thumbnail + label + trailing control) so the section feels consistent
 * regardless of `.existing` vs `.pending`. The remove button calls back into
 * the parent, which owns the eager `delete_pebble_media` flow; while
 * [isRemoving] the button is replaced by a spinner.
 *
 * The thumb URL signs lazily via [LocalSnapURLCache] (nullable by design —
 * previews render the placeholder); failures log and keep the placeholder,
 * never a user-facing error (iOS parity).
 */
@Composable
fun ExistingSnapRow(
    storagePath: String,
    isRemoving: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val snapUrls = LocalSnapURLCache.current
    var thumbUrl by remember(storagePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(storagePath, snapUrls) {
        if (snapUrls == null) return@LaunchedEffect
        try {
            thumbUrl = snapUrls.signedUrls(storagePath).thumb
        } catch (e: Exception) {
            Log.w(TAG, "thumb URL fetch failed for $storagePath", e)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val url = thumbUrl
        if (url != null) {
            AsyncImage(
                model = url,
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PebblesText(
                text = stringResource(R.string.photo_label),
                style = PebblesTypography.subhead,
                color = system.foreground,
            )
            MediaStateLabel(
                iconRes = R.drawable.ic_check_circle,
                text = stringResource(R.string.photo_state_saved),
                color = PebblesSuccess,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isRemoving) {
            Box(Modifier.size(48.dp), Alignment.Center) {
                CircularProgressIndicator(
                    color = PebblesTheme.colors.accent.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            RemovePhotoButton(onRemove)
        }
    }
}
