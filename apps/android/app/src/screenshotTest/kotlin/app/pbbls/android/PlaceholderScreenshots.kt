package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot-test previews for CI. The Compose Preview Screenshot Testing tool
 * renders every `@PreviewTest` here to a PNG on the JVM (no device/emulator),
 * which CI uploads as the `ui-screenshots` artifact so the UI is reviewable
 * without Android Studio. Keep these thin — they just host real composables from
 * `main` in light and dark. As real screens land (B/C/D), add a preview per
 * screen/state here.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun PlaceholderScreenLight() {
    PlaceholderScreen()
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PlaceholderScreenDark() {
    PlaceholderScreen()
}
