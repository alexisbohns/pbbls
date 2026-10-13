package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import java.time.OffsetDateTime

/**
 * Previews for Capture (#1048), one per step through the stateless
 * [CaptureFlowContent], plus the name step's three save states and the close
 * dialog with and without Save moment (R2). A fixed date: no clock in the
 * screenshot path.
 *
 * Render-to-view, not a regression gate — see `apps/android/CLAUDE.md`.
 */
private val capturedAt: OffsetDateTime = OffsetDateTime.parse("2026-10-11T18:05:27+02:00")

private fun pendingSnap(state: AttachedSnap.UploadState) =
    FormSnap.Pending(AttachedSnap(id = "preview-snap", localThumb = ByteArray(0), state = state))

@Composable
private fun CapturePreview(
    step: CaptureStep,
    name: String = "",
    snap: FormSnap? = null,
    seededFromPhoto: Boolean = false,
    @StringRes errorRes: Int? = null,
    closeConfirm: Boolean = false,
    closeOffersSave: Boolean = false,
) {
    val flow =
        CaptureFlowState(
            draft = CaptureDraft(name = name, happenedAt = capturedAt),
            step = step,
            hasSnap = snap != null,
            errorRes = errorRes,
        )
    // Previews are not interactions: the machine is there for the callbacks only, so it never buzzes.
    val model = CaptureFlowModel(haptic = {}, initial = flow)
    CaptureFlowContent(
        uiState =
            CaptureFlowUiState(
                flow = flow,
                snap = snap,
                seededFromPhoto = seededFromPhoto,
                isCloseConfirmPresented = closeConfirm,
                closeOffersSave = closeOffersSave,
            ),
        model = model,
        onPickPhoto = {},
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

/** Nothing picked yet: the add tile and Skip. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Capture_Photo() {
    PebblesTheme { CapturePreview(CaptureStep.PHOTO) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_PhotoUploading() {
    PebblesTheme { CapturePreview(CaptureStep.PHOTO, snap = pendingSnap(AttachedSnap.UploadState.UPLOADING)) }
}

/** The date seeded from the photo's EXIF, so the step says where it came from. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewWideTall
@Composable
fun Capture_When_FromPhoto() {
    PebblesTheme {
        CapturePreview(CaptureStep.WHEN, snap = pendingSnap(AttachedSnap.UploadState.UPLOADED), seededFromPhoto = true)
    }
}

/** No name and no photo: both saves disabled. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Capture_NameEmpty() {
    PebblesTheme { CapturePreview(CaptureStep.NAME) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_NameEmptyDark() {
    PebblesTheme { CapturePreview(CaptureStep.NAME) }
}

/** A photo alone is a moment: both saves enabled without a name. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Composable
fun Capture_NamePhotoOnly() {
    PebblesTheme { CapturePreview(CaptureStep.NAME, snap = pendingSnap(AttachedSnap.UploadState.UPLOADED)) }
}

/** A refused save stays on the step with the R10 copy. */
@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@PreviewLargeFontTall
@Composable
fun Capture_NameError() {
    PebblesTheme { CapturePreview(CaptureStep.NAME, name = "Swim at dawn", errorRes = R.string.moment_save_error_generic) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "fr", showBackground = true, heightDp = 720, locale = "fr")
@Preview(showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Capture_CloseConfirmWithSave() {
    PebblesTheme { CapturePreview(CaptureStep.NAME, name = "Swim at dawn", closeConfirm = true, closeOffersSave = true) }
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
