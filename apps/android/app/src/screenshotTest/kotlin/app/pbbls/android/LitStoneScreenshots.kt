package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * The lit stone and its seal (#1122 Part 3): the gallery and the four seeds from
 * `SpineLabStones` (debug sources, shared with the Spine lab), at rest: nothing is
 * pressed, so the glint and the ripple draw nothing.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneGalleryLight() {
    PebblesTheme { LitStoneGallery(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneGalleryDark() {
    PebblesTheme { LitStoneGallery(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun LitStoneSeedsLight() {
    PebblesTheme { LitStoneSeeds(pressable = false) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun LitStoneSeedsDark() {
    PebblesTheme { LitStoneSeeds(pressable = false) }
}
