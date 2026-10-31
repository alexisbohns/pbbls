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
import app.pbbls.android.features.home.ClaimStatus
import app.pbbls.android.features.home.HomeContent
import app.pbbls.android.features.home.HomeSection
import app.pbbls.android.features.home.HomeUiState
import app.pbbls.android.features.home.HomeWraps
import app.pbbls.android.features.home.checklist.ChecklistRow
import app.pbbls.android.features.home.checklist.ChecklistState
import app.pbbls.android.features.home.checklist.ChecklistTask
import app.pbbls.android.features.home.checklist.ChecklistTaskContent
import app.pbbls.android.features.home.checklist.FirstFiveBox
import app.pbbls.android.features.home.checklist.HomeChecklist
import app.pbbls.android.features.home.shortcuts.HomeShortcuts
import app.pbbls.android.features.path.wrap.month.HomeMonthGrid
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDateTime

/**
 * Home (#1383): the shell, the greeting and the quick actions (the default pebble row; the greeting
 * alone is also what a month read that failed with no copy draws, D9), and the month's grid over it
 * (D8), your first five (D11) and its task sheets, until the later parts add About. The monthly wraps
 * (D12): three months, the oldest folded; the promise card before the first; and a failed read keeping
 * its months. Paper, Night, French and 2 × font, plus a long name at 1 × to show the greeting shrinking
 * to hold its line (D18). The clock and the name are pinned,
 * so a render never depends on when it ran: Saturday 31 October 2026, 21:00, an ordinary evening.
 */
private val previewNow: LocalDateTime = LocalDateTime.of(2026, 10, 31, 21, 0)

/** The entry provider's filler, over the fixture's palettes (a preview has no palette service). */
private val previewMonth: HomeMonthSlot = { wrap, onWeek, modifier -> HomeMonthGrid(wrap, SpineFixtureWeek.lookups, onWeek, modifier) }

@Composable
private fun HomePreview(
    name: String = "Alexis",
    month: HomeSection<MonthWrap> = HomeSection.Loading,
    checklist: HomeChecklist? = null,
    wraps: HomeSection<HomeWraps> = HomeSection.Loading,
) {
    PebblesTheme {
        HomeContent(
            state = HomeUiState(name = name, month = month, checklist = checklist, wraps = wraps),
            now = previewNow,
            monthSlot = previewMonth,
            onWeek = {},
        )
    }
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

// MARK: - Your first five (D11)

/** The first [done] tasks ticked, in the fixed order: a deterministic checklist, no catalog. */
private fun checklist(
    done: Int,
    state: ChecklistState,
) = HomeChecklist(ChecklistTask.entries.mapIndexed { k, task -> ChecklistRow(task, k < done) }, state)

private val inProgress = checklist(done = 2, state = ChecklistState.IN_PROGRESS)
private val claimable = checklist(done = 5, state = ChecklistState.CLAIMABLE)

@Composable
private fun FirstFivePreview(content: @Composable () -> Unit) {
    PebblesTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { content() }
    }
}

/** The box in progress: two of five done (moment, pebble), three open with their chevrons. */
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun HomeFirstFiveProgressPaper() {
    FirstFivePreview { FirstFiveBox(inProgress, ClaimStatus.IDLE, onTask = {}, onClaim = {}) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeFirstFiveProgressNight() {
    FirstFivePreview { FirstFiveBox(inProgress, ClaimStatus.IDLE, onTask = {}, onClaim = {}) }
}

/** All five done: the claim row, then the same box after a claim that was not granted. */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeFirstFiveClaimablePaper() {
    FirstFivePreview {
        FirstFiveBox(claimable, ClaimStatus.IDLE, onTask = {}, onClaim = {})
        FirstFiveBox(claimable, ClaimStatus.FAILED, onTask = {}, onClaim = {})
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeFirstFiveClaimableNight() {
    FirstFivePreview { FirstFiveBox(claimable, ClaimStatus.IDLE, onTask = {}, onClaim = {}) }
}

/** The page with the box under the quick actions, as a first run shows it. */
@PreviewTest
@Preview(showBackground = true, heightDp = 1100)
@Composable
fun HomeFirstFivePage() {
    HomePreview(checklist = inProgress)
}

/** A task sheet's content on the sheet's own ground (the sheet itself is stock). */
@Composable
private fun TaskSheetPreview(task: ChecklistTask) {
    PebblesTheme {
        val ground = MaterialTheme.colorScheme.surfaceContainerLow
        ChecklistTaskContent(
            task = task,
            knockout = ground,
            onGo = {},
            modifier = Modifier.background(ground).padding(vertical = 16.dp),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeTaskSheetMoment() {
    TaskSheetPreview(ChecklistTask.MOMENT)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeTaskSheetPebble() {
    TaskSheetPreview(ChecklistTask.PEBBLE)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeTaskSheetCollection() {
    TaskSheetPreview(ChecklistTask.COLLECTION)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeTaskSheetSoul() {
    TaskSheetPreview(ChecklistTask.SOUL)
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeTaskSheetGlyph() {
    TaskSheetPreview(ChecklistTask.GLYPH)
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeTaskSheetNight() {
    TaskSheetPreview(ChecklistTask.SOUL)
}

// MARK: - The monthly wraps (D12)

@PreviewTest
@Preview(showBackground = true, heightDp = 1100)
@PreviewFrench
@Composable
fun HomeWrapsPaper() {
    HomePreview(month = HomeSection.Ready(HomeMonthFixtures.full), wraps = HomeSection.Ready(HomeWraps(HomeWrapFixtures.three)))
}

@PreviewTest
@Preview(showBackground = true, heightDp = 1100, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeWrapsNight() {
    HomePreview(month = HomeSection.Ready(HomeMonthFixtures.full), wraps = HomeSection.Ready(HomeWraps(HomeWrapFixtures.three)))
}

@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun HomeWrapsPromise() {
    HomePreview(wraps = HomeSection.Ready(HomeWraps(emptyList())))
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeWrapsStale() {
    HomePreview(wraps = HomeSection.Ready(HomeWraps(HomeWrapFixtures.three, stale = true)))
}

/** 2 × font, without the grid so the row is in view: the cards widen for "September" rather than break it. */
@PreviewTest
@Preview(name = "fs2", showBackground = true, fontScale = 2f, heightDp = 1100)
@Composable
fun HomeWrapsLargeFont() {
    HomePreview(wraps = HomeSection.Ready(HomeWraps(HomeWrapFixtures.three)))
}
