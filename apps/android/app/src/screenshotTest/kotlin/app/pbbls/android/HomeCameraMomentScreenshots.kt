package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.features.home.HomeContent
import app.pbbls.android.features.home.HomeUiState
import app.pbbls.android.features.home.camera.CameraMomentUiState
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime

/**
 * Home while a camera moment saves (#1383 D7, D6: the camera piece off at 0.6, "Saving your photo…"
 * under the quick actions) and after one failed (the failure line and its close). The clock and name
 * are pinned as in `HomeScreenshots`; no month read is in, so the greeting leads.
 */
private val previewNow: LocalDateTime = LocalDateTime.of(2026, 10, 31, 21, 0)

private val noMonth: HomeMonthSlot = { _, _, _ -> }

@Composable
private fun CameraPreview(camera: CameraMomentUiState) {
    PebblesTheme {
        HomeContent(state = HomeUiState(name = "Alexis"), now = previewNow, monthSlot = noMonth, onWeek = {}, camera = camera)
    }
}

@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@Composable
fun HomeCameraSaving() {
    CameraPreview(CameraMomentUiState(isSaving = true))
}

@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@Composable
fun HomeCameraUploadFailed() {
    CameraPreview(CameraMomentUiState(errorRes = R.string.home_camera_upload_failed))
}
