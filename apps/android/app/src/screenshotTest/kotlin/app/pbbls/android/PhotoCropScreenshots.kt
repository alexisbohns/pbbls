package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.CropSource
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.crop.CropSession
import app.pbbls.android.features.path.crop.CropTool
import app.pbbls.android.features.path.crop.PhotoCropEditor
import com.android.tools.screenshot.PreviewTest

/*
 * The crop editor (#1659) in light and dark, one render per tool: Ratio on the format the stand-in photo
 * opens on, Rotate straightened a little after a quarter turn.
 */

/** A stand-in photo, 3:4: a dusk gradient with a low hill, so the framing reads. */
private fun previewPhoto(): Bitmap {
    val bitmap = Bitmap.createBitmap(768, 1024, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, 1024f, 0xFFE9C9A6.toInt(), 0xFFB9C6D2.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 768f, 1024f, paint)
    paint.shader = null
    paint.color = 0xFF7F7966.toInt()
    canvas.drawOval(-160f, 640f, 940f, 1360f, paint)
    paint.color = 0xFFF4E3C3.toInt()
    canvas.drawCircle(560f, 300f, 90f, paint)
    return bitmap
}

@Composable
private fun CropPreview(tool: CropTool) {
    val session = CropSession(CropSource(image = previewPhoto(), captureDate = null), token = 1L)
    session.state.tool = tool
    if (tool == CropTool.ROTATE) {
        session.state.turnQuarter()
        session.state.setAngle(8.0)
    }
    PhotoCropEditor(session = session, onCancel = {}, onDone = {}, hapticsOn = { false })
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "dark", showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PhotoCrop_Ratio() {
    PebblesTheme { CropPreview(CropTool.RATIO) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 720)
@Preview(name = "dark", showBackground = true, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PhotoCrop_Rotate() {
    PebblesTheme { CropPreview(CropTool.ROTATE) }
}
