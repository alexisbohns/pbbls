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
import androidx.compose.material3.CircularProgressIndicator
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
import app.pbbls.android.features.path.components.NewPebbleButton
import app.pbbls.android.features.path.components.PathBottomBar
import app.pbbls.android.features.path.components.WeekHeader
import app.pbbls.android.features.path.components.WeekPebbleList
import app.pbbls.android.features.path.components.WeekRoll
import app.pbbls.android.features.path.create.CreatePebbleScreen
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.features.path.record.RecordFlowScreen
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.LocalDate
import kotlin.math.abs

/**
 * The Path timeline — the authenticated landing surface (`PathView.swift`
 * analog). Loads every pebble once via `path_pebbles()`, groups by ISO week,
 * pages the body by week, and hosts the create/detail/edit covers plus the
 * bottom stats bar. Sign-out moved to the Profile screen (sub-project C);
 * [onProfile] navigates there.
 *
 * Every piece of state it used to hold in `remember` now lives in
 * [PathViewModel] (#849), so a rotation no longer re-fetches the timeline,
 * loses the focused week or closes an open cover. What is left here is
 * composition: which cover is up, and wiring the stateless [PathContent] to the
 * state.
 *
 * The covers are still conditionally-composed children rather than navigation
 * destinations, so their own ViewModels are activity-scoped; #852 turns them
 * into a real back stack.
 */
@Composable
fun PathScreen(
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PathViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    // Coming back from a pushed screen must re-read the timeline: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background),
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
                    CircularProgressIndicator(
                        color = accent.primary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is PathUiState.Error ->
                    PebblesText(
                        text = stringResource((uiState as PathUiState.Error).messageRes),
                        style = PebblesTypography.body,
                        color = system.secondary,
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
                        onPebbleTap = { pebble -> viewModel.openDetail(pebble.id) },
                        onPebbleDelete = viewModel::requestDelete,
                        onCreatePebble = viewModel::openFlow,
                        onCreatePebbleLongPress = viewModel::openForm,
                        onOpenDrafts = viewModel::openDrafts,
                        draftCount = content.draftCount,
                        karma = content.karma,
                        ripple = content.ripple,
                        onProfile = onProfile,
                    )
                }
            }
        }

        // Full-screen detail cover (self-applies safeDrawingPadding, so it lives in
        // the OUTER Box) — the fullScreenCover analog (D5).
        covers.detailPebbleId?.let { detailId ->
            PebbleDetailScreen(
                pebbleId = detailId,
                reloadKey = covers.detailReloadKey,
                onDismiss = viewModel::closeDetail,
                onEditRequested = viewModel::openEdit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Full-screen edit cover — stacked over the detail cover (both opaque, so
        // the detail stays alive underneath, matching iOS EditPebbleSheet). Also
        // in the OUTER Box since EditPebbleScreen self-applies safeDrawingPadding.
        // On save it swaps back to the detail (D5), bumps detailReloadKey to reload
        // the revealed detail in place, and reloads the timeline.
        covers.editingPebbleId?.let { editId ->
            EditPebbleScreen(
                pebbleId = editId,
                onDismiss = viewModel::closeEdit,
                onSaved = viewModel::onEditSaved,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Full-screen record-flow cover (M58) — the default composer. Sibling of
        // the detail cover, in the OUTER (unpadded) Box since it self-applies
        // safeDrawingPadding/imePadding. On publish it reloads the timeline and
        // focuses the new pebble's week behind the still-visible success step; it
        // deliberately does NOT open the detail the way the form does, because the
        // user has just spent ten screens on this pebble and the success step
        // already showed it (D10).
        if (covers.isPresentingFlow) {
            RecordFlowScreen(
                onPublished = viewModel::onFlowPublished,
                onDismiss = viewModel::closeFlow,
                modifier = Modifier.fillMaxSize(),
                resumeDraftId = covers.resumingDraft?.id,
                onDraftSaved = viewModel::onFlowDraftSaved,
            )
        }

        // Full-screen create cover — the all-at-once form, now reached by
        // long-pressing "New pebble" (M58 D1). Sibling of the detail cover, also
        // in the OUTER (unpadded) Box since it self-applies
        // safeDrawingPadding/imePadding (C, the fullScreenCover analog D5). On
        // success it reveals the new pebble through the detail cover and reloads
        // the timeline — the flow deliberately does not.
        if (covers.isPresentingCreate) {
            CreatePebbleScreen(
                onCreated = viewModel::onFormCreated,
                onCancel = viewModel::closeForm,
                modifier = Modifier.fillMaxSize(),
                resumeDraftId = covers.resumingDraft?.id,
                onDraftSaved = viewModel::onFormDraftSaved,
            )
        }

        // Full-screen drafts cover (M47) — self-applies safeDrawingPadding, so it
        // belongs in the OUTER Box like its siblings. Composed BEFORE the create
        // cover in z-order so resuming a draft stacks the composer on top of it.
        if (covers.showsDrafts) {
            DraftsScreen(
                onResume = viewModel::resumeDraft,
                onDismiss = viewModel::closeDrafts,
                modifier = Modifier.fillMaxSize(),
                reloadKey = covers.draftsReloadKey,
            )
        }

        covers.pendingDeletion?.let { target ->
            DeleteConfirmDialog(
                pebbleName = target.name,
                onConfirm = viewModel::confirmDelete,
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
    onCreatePebbleLongPress: (() -> Unit)? = null,
    onOpenDrafts: () -> Unit = {},
    draftCount: Int = 0,
    karma: Int? = null,
    ripple: RippleSummary? = null,
    onProfile: () -> Unit = {},
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
                    PebblesText(
                        text = stringResource(R.string.drafts_entry, draftCount),
                        style = PebblesTypography.meta,
                        color = PebblesTheme.colors.system.secondary,
                    )
                }
            }
        }
        // Pinned "New pebble" entry — the PathView.safeAreaInset(.bottom) analog.
        NewPebbleButton(
            onTap = onCreatePebble,
            onLongPress = onCreatePebbleLongPress,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Bottom stats bar (sub-project B) — karma + ripple; all taps push
        // Profile (sub-project C), which now owns sign-out.
        PathBottomBar(
            karma = karma,
            ripple = ripple,
            onProfile = onProfile,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
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
@Composable
internal fun DeleteConfirmDialog(
    pebbleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.pebble_delete_confirm_title, pebbleName),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        text = {
            PebblesText(
                text = stringResource(R.string.pebble_delete_confirm_message),
                style = PebblesTypography.body,
                color = system.secondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                PebblesText(
                    text = stringResource(R.string.pebble_delete),
                    style = PebblesTypography.buttonLabel,
                    color = PebblesDestructive,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                PebblesText(
                    text = stringResource(R.string.action_cancel),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
    )
}

/** Delete-failure notice — a single-action AlertDialog dismissing back to the timeline. */
@Composable
private fun DeleteErrorDialog(onDismiss: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        text = {
            PebblesText(
                text = stringResource(R.string.pebble_delete_error),
                style = PebblesTypography.body,
                color = system.secondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                PebblesText(
                    text = stringResource(R.string.action_cancel),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
    )
}
