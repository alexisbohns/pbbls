package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.spine.SpineWeek
import com.android.tools.screenshot.PreviewTest

/**
 * The whole fixture week as `SpineWeek` lays it out (#1122 Part 5), at phone width,
 * Paper and Night, large font and French: the masthead, the opening, the fourteen
 * persons behind their fold, the contents, the seven days on the ground — Friday still,
 * blank stones among the pebbles — and the mouth. Then the empty current week: the
 * invitation in place of the feed. The week is a lazy list, so the page is given a
 * height tall enough to lay every item out; no network, ratios primed.
 */
private val PHONE_WIDTH = 390.dp
private val WEEK_HEIGHT = 5200.dp
private val EMPTY_HEIGHT = 720.dp

private val emptyWeek = WeekRollEntry(weekStart = SpineFixtureWeek.weekStart, moments = emptyList())

@Composable
private fun WeekPage(
    entry: WeekRollEntry,
    height: Dp,
) {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(PHONE_WIDTH).height(height)) {
            SpineWeek(
                entry = entry,
                today = SpineFixtureWeek.today,
                soulsById = SpineFixtureWeek.soulsById,
                paletteFor = SpineFixtureWeek.palettes,
                aspect = null,
                onOpen = {},
                onRequestDelete = {},
                onPreviousWeek = {},
                onNextWeek = {},
                onCreatePebble = {},
                ratioOverride = SpineFixtureWeek.ratios::get,
            )
        }
    }
}

@PreviewTest
@PreviewLargeFont
@PreviewFrench
@Preview(showBackground = true)
@Composable
fun SpineWeekLight() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WEEK_HEIGHT) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineWeekDark() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WEEK_HEIGHT) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SpineWeekEmptyLight() {
    PebblesTheme { WeekPage(emptyWeek, EMPTY_HEIGHT) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineWeekEmptyDark() {
    PebblesTheme { WeekPage(emptyWeek, EMPTY_HEIGHT) }
}
