package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineGround
import app.pbbls.android.features.path.spine.SpineLayout
import com.android.tools.screenshot.PreviewTest

/**
 * One pebble of the Spine's feed on the raked ground at phone width (#1122 Part 4),
 * Paper and Night: the fourteen-person picnic alone, for the people line's overflow.
 * The whole fixture week is `SpineWeekScreenshots`. No network, ratio primed.
 */
private val PHONE_WIDTH = 390.dp

@Composable
private fun SpineFeedPicnic() {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(PHONE_WIDTH)) {
            SpineGround(Modifier.matchParentSize())
            Column {
                FixturePebble(SpineFixtureWeek.picnic, Modifier.padding(top = SpineLayout.DAY_PAD_TOP, bottom = SpineLayout.DAY_PAD_BOTTOM))
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SpineFeedPicnicLight() {
    PebblesTheme { SpineFeedPicnic() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineFeedPicnicDark() {
    PebblesTheme { SpineFeedPicnic() }
}
