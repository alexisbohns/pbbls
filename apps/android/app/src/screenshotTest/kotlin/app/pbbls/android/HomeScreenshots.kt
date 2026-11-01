package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsLayout
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.features.home.HomeContent
import app.pbbls.android.features.home.HomeSection
import app.pbbls.android.features.home.HomeUiState
import app.pbbls.android.features.home.shortcuts.HomeShortcuts
import app.pbbls.android.features.path.wrap.month.HomeMonthGrid
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime

/**
 * Home (#1383): the shell, the greeting and the quick actions (the default pebble row; the greeting
 * alone is also what a month read that failed with no copy draws, D9), and the month's grid over it
 * (D8), until the later parts add the first five, the wraps and About. Paper, Night, French and
 * 2 × font, plus a long name at 1 × to show the greeting shrinking to hold its line (D18). The clock
 * and the name are pinned,
 * so a render never depends on when it ran: Saturday 31 October 2026, 21:00, an ordinary evening.
 */
private val previewNow: LocalDateTime = LocalDateTime.of(2026, 10, 31, 21, 0)

/** The entry provider's filler, over the fixture's palettes (a preview has no palette service). */
private val previewMonth: HomeMonthSlot = { wrap, onWeek, modifier -> HomeMonthGrid(wrap, SpineFixtureWeek.lookups, onWeek, modifier) }

@Composable
private fun HomePreview(
    name: String = "Alexis",
    month: HomeSection<MonthWrap> = HomeSection.Loading,
) {
    PebblesTheme { HomeContent(state = HomeUiState(name = name, month = month), now = previewNow, monthSlot = previewMonth, onWeek = {}) }
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

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun HomeMonthPaper() {
    HomePreview(month = HomeSection.Ready(HomeMonthFixtures.full))
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeMonthNight() {
    HomePreview(month = HomeSection.Ready(HomeMonthFixtures.full))
}

/**
 * The quick actions' four styles (#1376, D6): row and bento, stones and plain tiles, each bento led
 * by a different last-used shortcut, then the stone row with its camera piece dimmed while a camera
 * moment saves. At 2 × font the rows stack their pieces and the bentos grow past square (D18).
 */
@Composable
private fun HomeQuickActionsStyles() {
    PebblesTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HomeShortcuts(HomeShortcutsLayout.ROW, stones = true, lastUsed = HomeShortcut.CAMERA, onShortcut = {})
            HomeShortcuts(HomeShortcutsLayout.ROW, stones = false, lastUsed = HomeShortcut.CAMERA, onShortcut = {})
            HomeShortcuts(HomeShortcutsLayout.BENTO, stones = true, lastUsed = HomeShortcut.QUICK, onShortcut = {})
            HomeShortcuts(HomeShortcutsLayout.BENTO, stones = false, lastUsed = HomeShortcut.PEBBLE, onShortcut = {})
            HomeShortcuts(
                HomeShortcutsLayout.ROW,
                stones = true,
                lastUsed = HomeShortcut.CAMERA,
                onShortcut = {},
                cameraEnabled = false,
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360)
@PreviewLargeFont
@PreviewFrench
@Composable
fun HomeQuickActionsPaper() {
    HomeQuickActionsStyles()
}

@PreviewTest
@Preview(showBackground = true, widthDp = 360, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeQuickActionsNight() {
    HomeQuickActionsStyles()
}
