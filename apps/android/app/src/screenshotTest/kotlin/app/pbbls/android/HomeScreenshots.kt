package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.features.home.HomeContent
import app.pbbls.android.features.home.HomeUiState
import app.pbbls.android.features.path.wrap.month.HomeMonthGrid
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime

/**
 * Home (#1383), the shell: the greeting alone, until the later parts add the month, the quick
 * actions, the first five, the wraps and About. Paper, Night, French and 2 × font, plus a long name
 * at 1 × to show the greeting shrinking to hold its line (D18). The clock and the name are pinned,
 * so a render never depends on when it ran: Saturday 31 October 2026, 21:00, an ordinary evening.
 */
private val previewNow: LocalDateTime = LocalDateTime.of(2026, 10, 31, 21, 0)

/** The entry provider's filler, over the fixture's palettes (a preview has no palette service). */
private val previewMonth: HomeMonthSlot = { wrap, onWeek, modifier -> HomeMonthGrid(wrap, SpineFixtureWeek.lookups, onWeek, modifier) }

@Composable
private fun HomePreview(name: String = "Alexis") {
    PebblesTheme { HomeContent(state = HomeUiState(name = name), now = previewNow, monthSlot = previewMonth, onWeek = {}) }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun HomeShellPaper() {
    HomePreview()
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeShellNight() {
    HomePreview()
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeShellLongName() {
    HomePreview(name = "Alexandrine")
}
