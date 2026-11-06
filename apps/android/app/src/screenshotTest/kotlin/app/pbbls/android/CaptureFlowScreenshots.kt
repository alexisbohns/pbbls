package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.features.path.capture.CaptureDraft
import app.pbbls.android.features.path.capture.CaptureFlowContent
import app.pbbls.android.features.path.capture.CaptureFlowModel
import app.pbbls.android.features.path.capture.CaptureFlowState
import app.pbbls.android.features.path.capture.CaptureFlowUiState
import app.pbbls.android.features.path.capture.CaptureStep
import com.android.tools.screenshot.PreviewTest
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime

/**
 * Previews for Capture (#1048, #1515), one per step through the stateless
 * [CaptureFlowContent]: the photo step, the moment page empty, filled, uploading, failed and
 * chaining into Complete, and the close dialog with and without Save moment (R2). A fixed
 * date: no clock in the screenshot path.
 *
 * Render-to-view, not a regression gate — see `apps/android/CLAUDE.md`.
 */
private val capturedAt: OffsetDateTime = OffsetDateTime.parse("2026-10-11T18:05:27+02:00")

private fun pendingSnap(
    state: AttachedSnap.UploadState,
    withThumb: Boolean = false,
) = FormSnap.Pending(AttachedSnap(id = "preview-snap", localThumb = if (withThumb) previewThumb() else ByteArray(0), state = state))

/** A stand-in photo: a warm dusk gradient with a low hill, encoded the way the pipeline's thumb is (JPEG). */
private fun previewThumb(): ByteArray {
    val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, 420f, 0xFFE9C9A6.toInt(), 0xFFB9C6D2.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 420f, 420f, paint)
    paint.shader = null
    paint.color = 0xFF7F7966.toInt()
    canvas.drawOval(-80f, 270f, 500f, 560f, paint)
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

@Composable
private fun CapturePreview(
    step: CaptureStep,
    name: String = "",
    words: String = "",
    snap: FormSnap? = null,
    seededFromPhoto: Boolean = false,
    @StringRes errorRes: Int? = null,
    @StringRes blockedRes: Int? = null,
    closeConfirm: Boolean = false,
    closeOffersSave: Boolean = false,
    chainsComplete: Boolean = false,
    cameraAvailable: Boolean = false,
    isPicking: Boolean = false,
) {
    val flow =
        CaptureFlowState(
            draft = CaptureDraft(name = name, description = words, happenedAt = capturedAt),
            step = step,
            hasSnap = snap != null,
            seededFromPhoto = seededFromPhoto,
            errorRes = errorRes,
        )
    // Previews are not interactions: the machine is there for the callbacks only, so it never buzzes.
    val model = CaptureFlowModel(haptic = {}, initial = flow)
    CaptureFlowContent(
        uiState =
            CaptureFlowUiState(
                flow = flow,
                snap = snap,
                snapBlockedMessageRes = blockedRes,
                isCloseConfirmPresented = closeConfirm,
                closeOffersSave = closeOffersSave,
                chainsComplete = chainsComplete,
                cameraAvailable = cameraAvailable,
                isPicking = isPicking,
            ),
        model = model,
        onPickPhoto = {},
        onTakePhoto = {},
        onRetryPhoto = {},
        onRemovePhoto = {},
        onSave = {},
        onSaveThenComplete = {},
        onClose = {},
        onSaveFromClose = {},
        onKeepGoing = {},
        onDiscard = {},
    )
}

/** Nothing picked yet: Just write, the camera and the Library tile. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFontTall
@Composable
fun Capture_Photo() {
    PebblesTheme { CapturePreview(CaptureStep.PHOTO, cameraAvailable = true) }
}

/** No camera app: the camera button is not drawn, and "Just write" takes the row. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun Capture_PhotoNoCamera() {
    PebblesTheme { CapturePreview(CaptureStep.PHOTO) }
}

/** Back here through Change photo: the tile shows the current photo. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_PhotoPicked() {
    PebblesTheme {
        CapturePreview(CaptureStep.PHOTO, cameraAvailable = true, snap = pendingSnap(AttachedSnap.UploadState.UPLOADED, withThumb = true))
    }
}

/** A first pick being read, no photo yet: the tile's busy state. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_PhotoPicking() {
    PebblesTheme { CapturePreview(CaptureStep.PHOTO, cameraAvailable = true, isPicking = true) }
}

/** A new pick being read over the current photo: the photo, the scrim and the spinner. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_PhotoReplacing() {
    PebblesTheme {
        CapturePreview(
            CaptureStep.PHOTO,
            cameraAvailable = true,
            isPicking = true,
            snap = pendingSnap(AttachedSnap.UploadState.UPLOADED, withThumb = true),
        )
    }
}

/** A pick during its beat: the photo, and the spinner. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_PhotoUploading() {
    PebblesTheme {
        CapturePreview(CaptureStep.PHOTO, cameraAvailable = true, snap = pendingSnap(AttachedSnap.UploadState.UPLOADING, withThumb = true))
    }
}

/** Nothing typed and no photo: both saves disabled, "Add a photo". */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewWideTall
@PreviewLargeFontTall
@Composable
fun Capture_MomentEmpty() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT) }
}

/** The page as the iOS render shows it: a name, a date, a few words. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFontTall
@Composable
fun Capture_MomentFilled() {
    PebblesTheme {
        CapturePreview(CaptureStep.MOMENT, name = "Coffee with Léa", words = "We sat by the canal and talked until the light went.")
    }
}

/** Close to the limit: the counter shows. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun Capture_MomentCounter() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, name = "Swim at dawn", words = "w".repeat(250)) }
}

/** A photo still uploading, its date seeded from it: the ring, the label, and "From your photo". */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewWideTall
@Composable
fun Capture_MomentUploading() {
    PebblesTheme {
        // The blocked state the app is in while a photo uploads: both saves disabled.
        CapturePreview(
            CaptureStep.MOMENT,
            snap = pendingSnap(AttachedSnap.UploadState.UPLOADING),
            seededFromPhoto = true,
            blockedRes = R.string.pebble_save_error_photo_uploading,
        )
    }
}

/** A photo alone is a moment: both saves enabled without a name. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun Capture_MomentPhotoOnly() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, snap = pendingSnap(AttachedSnap.UploadState.UPLOADED), seededFromPhoto = true) }
}

/** An upload that failed: the orange label, Retry, and the saves held back. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Capture_MomentFailed() {
    PebblesTheme {
        CapturePreview(
            CaptureStep.MOMENT,
            name = "Swim at dawn",
            snap = pendingSnap(AttachedSnap.UploadState.FAILED),
            blockedRes = R.string.pebble_save_error_photo_failed,
        )
    }
}

/** Home's New pebble (#1383 D7): one save, and no "Just save". */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_MomentChainsComplete() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, name = "Swim at dawn", chainsComplete = true) }
}

/** A refused save stays on the page with the R10 copy. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Capture_MomentError() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, name = "Swim at dawn", errorRes = R.string.moment_save_error_generic) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_CloseConfirmWithSave() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, name = "Swim at dawn", closeConfirm = true, closeOffersSave = true) }
}

/** Words only cannot save: the dialog asks, and offers no Save moment. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun Capture_CloseConfirmWordsOnly() {
    PebblesTheme { CapturePreview(CaptureStep.MOMENT, words = "We sat by the canal.", closeConfirm = true) }
}

/** A photo still uploading: the dialog offers no Save moment. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Composable
fun Capture_CloseConfirm() {
    PebblesTheme {
        CapturePreview(CaptureStep.PHOTO, snap = pendingSnap(AttachedSnap.UploadState.UPLOADING), closeConfirm = true)
    }
}
