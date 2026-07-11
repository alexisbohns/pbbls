package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot-test previews for CI (see `apps/android/CLAUDE.md`). Renders the
 * whole design system — color tokens, type ramp, Rive logo — in light and
 * dark so the maintainer can review B without a device, and confirm Nunito
 * on a real screenshot.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun DebugTokenPreviewScreenLight() {
    PebblesTheme {
        DebugTokenPreviewScreen()
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DebugTokenPreviewScreenDark() {
    PebblesTheme {
        DebugTokenPreviewScreen()
    }
}
