package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.WeekNeighbour
import app.pbbls.android.features.path.spine.logic.SpineWeekBuilder
import com.android.tools.screenshot.PreviewTest

/**
 * The fixture week as `SpineWeek` lays it out (#1122 Part 5), at phone width, in three
 * windows of a tall page so no reference grows past the feed's: the verso (masthead,
 * opening, the fourteen persons behind their fold, the contents) in Paper and Night, large
 * font and French; the days from the first seam in Night (Monday's card and entry, Tuesday,
 * Wednesday with the named moment that is not a pebble yet); the last day and the mouth in
 * Night (Sunday's blank photo, Bruno's uncarved stone, the cairn). Then the empty current
 * week: the invitation in place of the feed. No network, ratios primed.
 */
private val PHONE_WIDTH = 390.dp
private val WINDOW_HEIGHT = 1800.dp
private val EMPTY_HEIGHT = 720.dp

private val emptyWeek = WeekRollEntry(weekStart = SpineFixtureWeek.weekStart, moments = emptyList())

/**
 * The list's rows before the first seam: the week line and the tiles (#1207). Coupled to `SpineWeek`'s
 * private `WeekItems` order; a row added before the days moves these windows.
 */
private const val VERSO_ROWS = 2

private val week by lazy { SpineWeekBuilder.build(SpineFixtureWeek.entry, SpineFixtureWeek.zone, SpineFixtureWeek.today) }
private val firstSeamRow: Int get() = VERSO_ROWS
private val lastSeamRow: Int get() = VERSO_ROWS + week.days.dropLast(1).sumOf { 1 + it.moments.size }

@Composable
private fun WeekPage(
    entry: WeekRollEntry,
    height: Dp,
    firstRow: Int = 0,
) {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(PHONE_WIDTH).height(height)) {
            SpineWeek(
                hapticsOn = { true },
                entry = entry,
                today = SpineFixtureWeek.today,
                soulsById = SpineFixtureWeek.soulsById,
                paletteFor = SpineFixtureWeek.palettes,
                aspect = null,
                onOpen = { _, _ -> },
                onRequestDelete = {},
                // Settled, so every tile shows; an empty week has no wrap tiles to show.
                wrap = if (entry.moments.isEmpty()) null else SpineFixtureWeek.wrap(live = false),
                lookups = SpineFixtureWeek.lookups,
                nextWeek = WeekNeighbour(SpineFixtureWeek.nextNumber) {},
                onCreatePebble = {},
                listState = remember(firstRow) { LazyListState(firstVisibleItemIndex = firstRow) },
                zone = SpineFixtureWeek.zone,
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
fun SpineWeekVersoLight() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WINDOW_HEIGHT) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineWeekVersoDark() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WINDOW_HEIGHT) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineWeekDaysDark() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WINDOW_HEIGHT, firstRow = firstSeamRow) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SpineWeekMouthDark() {
    PebblesTheme { WeekPage(SpineFixtureWeek.entry, WINDOW_HEIGHT, firstRow = lastSeamRow) }
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
