package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.home.quick.QuickMomentForm
import app.pbbls.android.features.home.quick.QuickMomentUiState
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home's quick moment sheet (#1383 D7), its content on the sheet's colour: empty (both saves off),
 * filled, and a refused save. The sheet itself is a window a preview cannot draw. The time is pinned
 * in the host's own zone, so the pills read Saturday 31 October 2026, 21:00 wherever it renders, and
 * the field is not focused, so no cursor blinks into the image.
 */
private val previewWhen = LocalDateTime.of(2026, 10, 31, 21, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime()

@Composable
private fun QuickPreview(form: QuickMomentUiState) {
    PebblesTheme {
        QuickMomentForm(
            form = form,
            onNameChange = {},
            onWhenChange = {},
            onSave = {},
            autoFocus = false,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 24.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@Composable
fun QuickMomentEmpty() {
    QuickPreview(QuickMomentUiState(happenedAt = previewWhen))
}

@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewLargeFont
@Composable
fun QuickMomentFilled() {
    QuickPreview(QuickMomentUiState(name = "Coffee with Mo", happenedAt = previewWhen))
}

@PreviewTest
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@Composable
fun QuickMomentError() {
    QuickPreview(QuickMomentUiState(name = "Coffee with Mo", happenedAt = previewWhen, errorRes = R.string.home_quick_error))
}
