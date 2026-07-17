package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.pebblemedia.AttachedPhotoView
import app.pbbls.android.features.pebblemedia.ExistingSnapRow
import app.pbbls.android.features.pebblemedia.models.AttachedSnap
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Pebble media previews (M42 #581): the form photo chip in its three upload
 * states and the saved-snap row (placeholder thumb — previews have no
 * SnapURLCache), light and dark. The empty decode byte-array exercises the
 * thumbnail placeholder path.
 */
private fun snap(state: AttachedSnap.UploadState) =
    AttachedSnap(
        id = "preview-snap",
        localThumb = ByteArray(0),
        state = state,
    )

@Composable
private fun MediaGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp)
                .width(360.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AttachedPhotoView(snap = snap(AttachedSnap.UploadState.UPLOADING), onRetry = {}, onRemove = {})
        AttachedPhotoView(snap = snap(AttachedSnap.UploadState.UPLOADED), onRetry = {}, onRemove = {})
        AttachedPhotoView(snap = snap(AttachedSnap.UploadState.FAILED), onRetry = {}, onRemove = {})
        ExistingSnapRow(storagePath = "uid/sid", isRemoving = false, onRemove = {})
        ExistingSnapRow(storagePath = "uid/sid", isRemoving = true, onRemove = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun MediaGalleryLight() {
    PebblesTheme { MediaGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun MediaGalleryDark() {
    PebblesTheme { MediaGallery() }
}
