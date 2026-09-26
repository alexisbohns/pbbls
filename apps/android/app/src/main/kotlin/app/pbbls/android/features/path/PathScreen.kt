package app.pbbls.android.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.components.NewPebbleFab
import app.pbbls.android.features.path.components.WeekHeader
import app.pbbls.android.features.path.components.WeekPebbleList
import app.pbbls.android.features.path.components.WeekRoll
import java.time.LocalDate
import kotlin.math.abs

/**
 * The Path timeline — the authenticated landing surface (`PathView.swift`
 * analog). Loads every pebble once via `path_pebbles()`, groups by ISO week,
 * pages the body by week, and shows the karma/Ripples status above the roll.
 * Sign-out lives on the Profile screen, which is the `You` tab (#852).
 *
 * Every piece of state it used to hold in `remember` now lives in
 * [PathViewModel] (#849), so a rotation no longer re-fetches the timeline or
 * loses the focused week.
 *
 * Detail, edit, both composers and drafts are `PebblesEntryProvider` entries
 * now (#852) — [onOpenDetail], [onOpenDrafts], [onCreatePebble] and
 * [onCreatePebbleLongPress] are how this screen reaches them; only the caller
 * (`PebblesEntryProvider`) ever touches a `Navigator`.
 *
 * @param onDeleteConfirmed Called with the deleted pebble's id once the user
 *   confirms, so a detail open beside Path can close (#940). It runs on
 *   confirm, not on success: if the delete fails, the error dialog says so and
 *   the pebble can be reopened.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PathScreen(
    onOpenDetail: (String) -> Unit,
    onOpenDrafts: () -> Unit,
    onCreatePebble: () -> Unit,
    onCreatePebbleLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteConfirmed: (pebbleId: String) -> Unit = {},
    viewModel: PathViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    // Coming back from a pushed screen must re-read the timeline: the ViewModel is
    // scoped to the back stack entry, which survives that round trip. This is also
    // how a published pebble, a saved edit or a delete made on the detail/edit/
    // drafts entries reaches the timeline now — see PathViewModel.onResumed.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val palettes = LocalEmotionPaletteService.current
    val colors = MaterialTheme.colorScheme

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
        ) {
            // Exhaustive with no `else` — a new PathUiState case must be rendered.
            when (uiState) {
                PathUiState.Loading ->
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )

                is PathUiState.Error ->
                    Text(
                        text = stringResource((uiState as PathUiState.Error).messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is PathUiState.Content -> {
                    val content = uiState as PathUiState.Content
                    PathContent(
                        entries = content.entries,
                        initialWeekStart = content.focusedWeekStart,
                        focusedWeekStart = content.focusedWeekStart,
                        today = content.today,
                        onFocusChange = viewModel::onFocusWeek,
                        paletteFor = { pebble -> pebble.emotion?.let { palettes.palette(it.id) } },
                        onPebbleTap = { pebble -> onOpenDetail(pebble.id) },
                        onPebbleDelete = viewModel::requestDelete,
                        onCreatePebble = onCreatePebble,
                        onOpenDrafts = onOpenDrafts,
                        draftCount = content.draftCount,
                        // The timeline alone is capped (#855): the FAB below
                        // stays anchored to the window's bottom-end corner.
                        modifier = Modifier.readableWidth(),
                    )
                }
            }

            // "New pebble" (#852, D3). It lives HERE, inside the Path entry,
            // rather than in `RootScreen`'s Scaffold: the create destinations
            // are reached through the lambdas `PebblesEntryProvider` binds to
            // THIS entry, and a Scaffold-level FAB sits outside every entry, so
            // it would have to be told which screen is showing and what its
            // actions are — exactly the coupling the entry provider exists to
            // avoid. It is also Path-only chrome, which the Scaffold is not.
            //
            // It floats clear of the bottom bar because the navigation suite lays
            // the Path entry out above it, and on large screens it keeps the window
            // corner opposite the rail (#855). Tap opens the record flow,
            // long-press the all-at-once composer (M58 D1).
            if (uiState is PathUiState.Content) {
                NewPebbleFab(
                    onClick = onCreatePebble,
                    onLongClick = onCreatePebbleLongPress,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(PebblesTheme.spacing.lg),
                )
            }
        }

        covers.pendingDeletion?.let { target ->
            DeleteConfirmDialog(
                pebbleName = target.name,
                onConfirm = {
                    // `target` was captured before confirmDelete clears it.
                    viewModel.confirmDelete()
                    onDeleteConfirmed(target.id)
                },
                onDismiss = viewModel::cancelDelete,
            )
        }
        if (covers.didDeleteFail) DeleteErrorDialog(onDismiss = viewModel::dismissDeleteError)
    }
}

/**
 * Stateless timeline layout — separated from [PathScreen] so screenshot
 * previews can drive it with fixture data (no services, no network).
 *
 * Week-focus has a single source of truth (the caller's `focusedWeekStart`);
 * the pager and the roll/header both follow it: a swipe reports the new page
 * through [onFocusChange], a chevron/cairn tap changes focus and the pager
 * animates to it. Scrolling to the already-current page is a no-op, so the
 * two effects settle.
 */
@Composable
fun PathContent(
    entries: List<WeekRollEntry>,
    initialWeekStart: LocalDate,
    focusedWeekStart: LocalDate,
    today: LocalDate,
    onFocusChange: (LocalDate) -> Unit,
    paletteFor: (Pebble) -> EmotionPalette?,
    onPebbleTap: (Pebble) -> Unit = {},
    onPebbleDelete: (Pebble) -> Unit = {},
    onCreatePebble: () -> Unit = {},
    onOpenDrafts: () -> Unit = {},
    draftCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val initialIndex =
        entries.indexOfFirst { it.weekStart == initialWeekStart }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { entries.size }

    // Swipe → focus. Keyed on entries so a (future) reload re-subscribes.
    LaunchedEffect(pagerState, entries) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            entries.getOrNull(page)?.let { onFocusChange(it.weekStart) }
        }
    }
    // Focus (chevron / cairn tap) → pager.
    LaunchedEffect(focusedWeekStart) {
        val index = entries.indexOfFirst { it.weekStart == focusedWeekStart }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekRoll(
            entries = entries,
            focusedWeekStart = focusedWeekStart,
            onFocusChange = onFocusChange,
            modifier = Modifier.fillMaxWidth(),
        )
        WeekHeader(
            entries = entries,
            focusedWeekStart = focusedWeekStart,
            today = today,
            onFocusChange = onFocusChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
        )
        HorizontalPager(
            state = pagerState,
            key = { entries[it].weekStart.toEpochDay() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),
        ) { page ->
            WeekPebbleList(
                entry = entries[page],
                paletteFor = paletteFor,
                onPebbleTap = onPebbleTap,
                onPebbleDelete = onPebbleDelete,
                onCreatePebble = onCreatePebble,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Drafts entry (M47), above the create button so it reads as "unfinished
        // business first". Hidden at zero so it is never dead chrome.
        if (draftCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenDrafts) {
                    Text(
                        text = stringResource(R.string.drafts_entry, draftCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The iOS `PathView.load()` refocus rule: keep the focused week if it still
 * exists; otherwise prefer the current week; otherwise the entry closest in
 * time. Null only for an empty roll (unreachable — the builder always emits
 * the current week).
 */
internal fun refocusedWeekStart(
    entries: List<WeekRollEntry>,
    focused: LocalDate?,
    today: LocalDate,
): LocalDate? {
    if (entries.isEmpty()) return null
    if (focused != null && entries.any { it.weekStart == focused }) return focused
    val currentStart = WeekRollBuilder.weekStart(today)
    entries.firstOrNull { it.weekStart == currentStart }?.let { return it.weekStart }
    val anchor = focused ?: currentStart
    return entries.minByOrNull { abs(it.weekStart.toEpochDay() - anchor.toEpochDay()) }?.weekStart
}

/**
 * Destructive-delete confirmation — the `PathView.confirmationDialog` analog
 * (D8): "Delete <name>? This can't be undone." with a destructive Delete and a
 * Cancel. `internal` (not `private`) so the screenshot preview can drive it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeleteConfirmDialog(
    pebbleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.pebble_delete_confirm_title, pebbleName),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.pebble_delete_confirm_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.pebble_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}

/** Delete-failure notice — a single-action AlertDialog dismissing back to the timeline. */
@Composable
private fun DeleteErrorDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        text = {
            Text(
                text = stringResource(R.string.pebble_delete_error),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}
