package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.month.HomeMonthGrid
import com.android.tools.screenshot.PreviewTest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Home's month grid (#1383 D8) on the fixture's October, at Home's gutter: Paper, Night, French and 2 × font,
 * plus a quiet month. The month is [SpineFixtureWeek.monthWrap] given all five of October's weeks: September's
 * tail (outside days), the fixture week (every pebble shape, three a day at most), and three weeks without a
 * pebble (sockets and dotted cairns). The server's own `month-full.json` is not reachable here: the preview
 * renderer does not load the test source set's resources.
 */
internal object HomeMonthFixtures {
    private fun emptyWeek(
        monday: LocalDate,
        from: Int,
        to: Int,
    ) = MonthWeek(monday, from, to, 0, emptyList(), List(7) { DayWeight(0, 0) })

    private val quietWeeks =
        listOf(
            emptyWeek(LocalDate.of(2026, 10, 12), 11, 17),
            emptyWeek(LocalDate.of(2026, 10, 19), 18, 24),
            emptyWeek(LocalDate.of(2026, 10, 26), 25, 30),
        )

    val full: MonthWrap by lazy {
        val fixture = SpineFixtureWeek.monthWrap
        fixture.copy(weeks = fixture.weeks.orEmpty() + quietWeeks)
    }

    /** October with no pebble at all: every day a socket, every cairn dotted. */
    val quiet: MonthWrap by lazy {
        full.copy(
            pebbles = 0,
            people = 0,
            cover = null,
            weeks = listOf(emptyWeek(LocalDate.of(2026, 9, 28), 0, 3), emptyWeek(LocalDate.of(2026, 10, 5), 4, 10)) + quietWeeks,
        )
    }

    /** November 2026 as the server sends it below one pebble: no weeks, no cover, only its frame (a Sunday 1st). */
    val emptyNovember: MonthWrap by lazy {
        full.copy(
            month = YearMonth.of(2026, 11),
            daysInMonth = 30,
            firstWeekday = DayOfWeek.SUNDAY,
            pebbles = 0,
            people = 0,
            cover = null,
            weeks = null,
        )
    }
}

@Composable
private fun GridPreview(wrap: MonthWrap) {
    PebblesTheme {
        Surface(color = PebblesTheme.spine.colors.ground) {
            HomeMonthGrid(wrap, SpineFixtureWeek.lookups, onWeek = {}, modifier = Modifier.padding(16.dp))
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390)
@PreviewLargeFont
@PreviewFrench
@Composable
fun HomeMonthGridPaper() {
    GridPreview(HomeMonthFixtures.full)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeMonthGridNight() {
    GridPreview(HomeMonthFixtures.full)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390)
@Composable
fun HomeMonthGridQuiet() {
    GridPreview(HomeMonthFixtures.quiet)
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390)
@Composable
fun HomeMonthGridEmptyMonth() {
    GridPreview(HomeMonthFixtures.emptyNovember)
}
