package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.features.path.PathContent
import app.pbbls.android.features.path.strip.PathPage
import app.pbbls.android.features.path.strip.PathPages
import app.pbbls.android.features.path.wrap.WrapState
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate
import java.time.YearMonth

/**
 * Full-screen previews of the Path (#531, #1122, #1207): the strip over the fixture week on
 * the Spine (`SpineFixtureWeek`, the week of 5 October 2026 with "today" its Sunday). The
 * strip holds settled cairns, an empty week's ground line, September's dolmen and the live
 * week; a week before the fixture gives the masthead its way back. Also the fresh current
 * week — light and dark. Driven through the stateless [PathContent] with fixture
 * data (no services; the photo ratios primed, the photos themselves withheld).
 *
 * The timeline and its "today" are internal so the Path list-detail renders show this
 * exact timeline (#940).
 */

internal val pathPreviewToday: LocalDate = SpineFixtureWeek.today

internal val populatedEntries: List<WeekRollEntry> =
    listOf(
        WeekRollEntry(weekStart = SpineFixtureWeek.weekStart.minusWeeks(1), moments = emptyList()),
        SpineFixtureWeek.entry,
    )

private fun cairn(vararg days: Pair<Int, Int>): List<DayWeight> = days.map { (moments, intensity) -> DayWeight(moments, intensity) }

/** The strip `path_strip` would send around the fixture: what the cells draw, not what the feed holds. */
private val previewStrip: Strip =
    Strip(
        listOf(
            StripItem.Week(
                LocalDate.of(2026, 9, 7),
                37,
                2026,
                4,
                true,
                false,
                cairn(1 to 2, 0 to 0, 2 to 3, 0 to 0, 1 to 1, 0 to 0, 0 to 0),
            ),
            StripItem.Week(LocalDate.of(2026, 9, 14), 38, 2026, 0, false, false, cairn()),
            StripItem.Week(
                LocalDate.of(2026, 9, 21),
                39,
                2026,
                6,
                true,
                false,
                cairn(1 to 1, 1 to 2, 0 to 0, 3 to 3, 0 to 0, 1 to 0, 0 to 0),
            ),
            StripItem.Week(
                LocalDate.of(2026, 9, 28),
                40,
                2026,
                2,
                true,
                false,
                cairn(0 to 0, 1 to 2, 0 to 0, 0 to 0, 1 to 1, 0 to 0, 0 to 0),
            ),
            StripItem.Month(YearMonth.of(2026, 9), 9),
            StripItem.Week(
                LocalDate.of(2026, 10, 5),
                41,
                2026,
                13,
                true,
                true,
                cairn(2 to 3, 3 to 2, 1 to 1, 4 to 3, 1 to 2, 1 to 1, 1 to 2),
            ),
        ),
    )

internal fun previewStripItems(entries: List<WeekRollEntry>) = PathPages.items(previewStrip, entries, pathPreviewToday)

/** The fixture week's wrap on its own Sunday: live, so contents and persons only, dashed and *so far*. */
internal fun previewWeekWraps(entries: List<WeekRollEntry>): Map<LocalDate, WrapState<WeekWrap>> =
    if (entries.any {
            it.moments.isNotEmpty()
        }
    ) {
        mapOf(SpineFixtureWeek.weekStart to WrapState.Ready(SpineFixtureWeek.wrap(live = true)))
    } else {
        emptyMap()
    }

private val emptyWeekEntries: List<WeekRollEntry> =
    listOf(WeekRollEntry(weekStart = SpineFixtureWeek.weekStart, moments = emptyList()))

@Composable
private fun ScreenPreview(entries: List<WeekRollEntry>) {
    PathContent(
        entries = entries,
        stripItems = previewStripItems(entries),
        focus = PathPage.Week(SpineFixtureWeek.weekStart),
        weekWraps = previewWeekWraps(entries),
        lookups = SpineFixtureWeek.lookups,
        today = pathPreviewToday,
        soulsById = SpineFixtureWeek.soulsById,
        onFocusChange = {},
        paletteFor = SpineFixtureWeek.palettes,
        // Mirrors PathScreen: the timeline is the readable column (#855), a
        // no-op at phone widths.
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .readableWidth(),
        ratioOverride = SpineFixtureWeek.ratios::get,
    )
}

@PreviewTest
@Preview(showBackground = true)
@PreviewWide
@PreviewLargeFont
@PreviewFrench
@Composable
fun PathScreenPopulatedLight() {
    PebblesTheme { ScreenPreview(populatedEntries) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PathScreenPopulatedDark() {
    PebblesTheme { ScreenPreview(populatedEntries) }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PathScreenFreshWeekLight() {
    PebblesTheme { ScreenPreview(emptyWeekEntries) }
}
