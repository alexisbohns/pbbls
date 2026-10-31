package app.pbbls.android.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.features.home.camera.CameraMomentStatus
import app.pbbls.android.features.home.camera.CameraMomentUiState
import app.pbbls.android.features.home.checklist.ChecklistTask
import app.pbbls.android.features.home.checklist.FirstFiveBox
import app.pbbls.android.features.home.components.HomeGreetingBlock
import app.pbbls.android.features.home.shortcuts.HomeShortcuts
import java.time.LocalDate
import java.time.LocalDateTime

/** The page's gutters: 16 dp to the sections, 4 more to the greeting's text (the mock's). */
private val GUTTER = 16.dp
private val TEXT_INSET = 4.dp
private val TOP = 22.dp
private val BOTTOM = 40.dp

/** Under the month's grid, to the greeting (the mock's 22 px). */
private val MONTH_GAP = 22.dp

/** From the greeting's line to the quick actions (the mock's 26 px). */
private val SHORTCUTS_GAP = 26.dp

/** Between two sections (the mock's 44 px gap). */
private val SECTION_GAP = 44.dp

/**
 * Home's page (#1383), stateless over [HomeUiState]: what the screenshot tests render. [now] pins the
 * greeting, so a preview passes a fixed time; [HomeScreen] passes the live minute. No top bar: the
 * greeting is the title, as on iOS. The column is the readable width ([PebblesScreen]).
 *
 * Above the greeting, the live month's grid through [monthSlot] (D8), once its read is in (D9); [onWeek]
 * gets a tapped column's Monday. It shares the greeting's item, so a grid landing while the page is at
 * its top shows rather than being inserted above the list's anchor.
 *
 * Under the quick actions, the camera moment's progress and failure lines ([camera], #1383 D7); its
 * piece is off while it saves (D6). [snackbar] hosts "Moment saved · 14:32" once one lands.
 *
 * Then your first five (D11) while it is in progress or claimable: [onTask] opens a task's sheet,
 * [onClaim] claims the blue pebble.
 *
 * The date is formatted in the app's locale, read here in composition (D22), never `Locale.getDefault()`.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    now: LocalDateTime,
    monthSlot: HomeMonthSlot,
    onWeek: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onShortcut: (HomeShortcut) -> Unit = {},
    hapticsOn: () -> Boolean = { true },
    camera: CameraMomentUiState = CameraMomentUiState(),
    onDismissCameraError: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    onTask: (ChecklistTask) -> Unit = {},
    onClaim: () -> Unit = {},
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val greeting = remember(now, state.name, resources, locale) { HomeGreeting.lines(now, state.name, resources, locale) }
    PebblesScreen(
        modifier = modifier.testTag(JourneyTags.HOME),
        // The camera moment's snackbar (D7), at the foot of the window over the page.
        overlay = { snackbar?.let { SnackbarHost(it, Modifier.align(Alignment.BottomCenter)) } },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = TOP, bottom = BOTTOM),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            item(key = "greeting") {
                Column {
                    val month = state.month
                    if (month is HomeSection.Ready) {
                        monthSlot(month.value, onWeek, Modifier.fillMaxWidth().padding(bottom = MONTH_GAP))
                    }
                    HomeGreetingBlock(lines = greeting, modifier = Modifier.padding(horizontal = TEXT_INSET))
                    // The quick actions sit with the greeting, 26 dp under its line (the mock's header).
                    HomeShortcuts(
                        layout = state.shortcuts.layout,
                        stones = state.shortcuts.stones,
                        lastUsed = state.shortcuts.lastUsed,
                        onShortcut = onShortcut,
                        hapticsOn = hapticsOn,
                        cameraEnabled = !camera.isSaving,
                        modifier = Modifier.padding(top = SHORTCUTS_GAP),
                    )
                    // Under the piece that was tapped, where the eye is, rather than at the foot of the page.
                    CameraMomentStatus(
                        state = camera,
                        onDismissError = onDismissCameraError,
                        modifier = Modifier.padding(top = Spacing.md, start = TEXT_INSET, end = TEXT_INSET),
                    )
                }
            }
            state.checklist?.takeIf { it.isShown }?.let { checklist ->
                item(key = "first-five") {
                    FirstFiveBox(checklist = checklist, claim = state.claim, onTask = onTask, onClaim = onClaim)
                }
            }
        }
    }
}
