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
import app.pbbls.android.features.path.PathContent
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate

/**
 * Full-screen previews of the Path (#531, #1122): the roll over the fixture week on the
 * Spine (`SpineFixtureWeek`, the week of 5 October 2026 with "today" its Sunday), with a
 * week before it so the roll has two cells and the masthead its way back, and the fresh
 * current week — light and dark. Driven through the stateless [PathContent] with fixture
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

private val emptyWeekEntries: List<WeekRollEntry> =
    listOf(WeekRollEntry(weekStart = SpineFixtureWeek.weekStart, moments = emptyList()))

@Composable
private fun ScreenPreview(entries: List<WeekRollEntry>) {
    val focused = SpineFixtureWeek.weekStart
    PathContent(
        entries = entries,
        initialWeekStart = focused,
        focusedWeekStart = focused,
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
