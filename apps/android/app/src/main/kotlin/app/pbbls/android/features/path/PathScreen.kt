package app.pbbls.android.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.components.NewPebbleFab
import app.pbbls.android.features.path.components.WeekRoll
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.WeekNeighbour
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import kotlin.math.abs

/**
 * The Path timeline — the authenticated landing surface (`PathView.swift`
 * analog). Loads every moment once via `path_moments()`, groups by ISO week,
 * pages the body by week, and shows the karma/Ripples status above the roll.
 * Sign-out lives on the Profile screen, which is the `You` tab (#852).
 *
 * Every piece of state it used to hold in `remember` now lives in
 * [PathViewModel] (#849), so a rotation no longer re-fetches the timeline or
 * loses the focused week.
 *
 * Detail, edit and Capture are `PebblesEntryProvider` entries
 * now (#852) — [onOpenDetail] and [onCreatePebble] are how
 * this screen reaches them; only the caller (`PebblesEntryProvider`) ever
 * touches a `Navigator`.
 *
 * @param onDeleteConfirmed Called with the deleted moment's id once the user
 *   confirms deleting the moment (not only its pebble), so a detail open
 *   beside Path can close (#940). It runs on
 *   confirm, not on success: if the delete fails, the error dialog says so and
 *   the pebble can be reopened.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PathScreen(
    onOpenDetail: (String) -> Unit,
    onCreatePebble: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteConfirmed: (momentId: String) -> Unit = {},
    viewModel: PathViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    // Coming back from a pushed screen must re-read the timeline: the ViewModel is
    // scoped to the back stack entry, which survives that round trip. This is also
    // how a captured moment, a completed pebble, a saved edit or a delete made on
    // the other entries reaches the timeline now — see PathViewModel.onResumed.
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
                        soulsById = content.soulsById,
                        onFocusChange = viewModel::onFocusWeek,
                        paletteFor = { moment -> moment.pebble?.emotion?.let { palettes.palette(it.id) } },
                        aspect = viewModel.snapAspect,
                        onPebbleTap = { moment -> onOpenDetail(moment.id) },
                        onPebbleDelete = viewModel::requestDelete,
                        onCreatePebble = onCreatePebble,
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
            // corner opposite the rail (#855). A tap opens Capture (#1048).
            if (uiState is PathUiState.Content) {
                NewPebbleFab(
                    onClick = onCreatePebble,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(PebblesTheme.spacing.lg)
                            .testTag(JourneyTags.NEW_PEBBLE),
                )
            }
        }

        covers.pendingDeletion?.let { target ->
            MomentDeleteDialog(
                hasPebble = target.isCompleted,
                onConfirm = { choice ->
                    // `target` was captured before confirmDelete clears it.
                    viewModel.confirmDelete(choice)
                    // Only a deleted moment closes its read page; without just
                    // its pebble it stays, as a bare moment (R8).
                    if (choice == MomentDeleteChoice.MOMENT) onDeleteConfirmed(target.id)
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
 * The roll, then the pager; each page is the week on the Spine (#1122, spec
 * §Chrome D5): the masthead and the mouth carry the week links the old header
 * held, so there is no header between the two. Week-focus has a single source
 * of truth (the caller's `focusedWeekStart`); the pager and the roll both
 * follow it: a swipe reports the new page through [onFocusChange], a roll tap
 * or a week link changes focus and the pager animates to it. Scrolling to the
 * already-current page is a no-op, so the two effects settle.
 *
 * @param soulsById the souls the persons rows and people lines draw glyphs
 *   from; a soul absent from it is skipped.
 * @param aspect the photo-ratio probe the cards read; null (previews) keeps
 *   every field square unless [ratioOverride] answers.
 * @param ratioOverride preview-only: a primed ratio by storage prefix, so a
 *   render never waits on a probe. Production passes nothing.
 */
@Composable
fun PathContent(
    entries: List<WeekRollEntry>,
    initialWeekStart: LocalDate,
    focusedWeekStart: LocalDate,
    today: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    onFocusChange: (LocalDate) -> Unit,
    paletteFor: (PathMoment) -> EmotionPalette?,
    modifier: Modifier = Modifier,
    aspect: SnapAspectCache? = null,
    onPebbleTap: (PathMoment) -> Unit = {},
    onPebbleDelete: (PathMoment) -> Unit = {},
    onCreatePebble: () -> Unit = {},
    ratioOverride: ((String) -> Float?)? = null,
) {
    val initialIndex =
        entries.indexOfFirst { it.weekStart == initialWeekStart }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { entries.size }

    // Programmatic scrolls in flight. A counter, not a flag: a new focus
    // cancels the running scroll, whose cleanup can land after the next one
    // has started. Not snapshot state: only the collector below reads it.
    val steering = remember { intArrayOf(0) }

    // Swipe → focus. Keyed on entries so a reload re-subscribes; the page in
    // place at (re)subscription is dropped, because a reload is not a page
    // change and reporting it would overwrite a requested focus (R14). Pages
    // the pager passes while it is being steered are skipped too, or a jump
    // several weeks away would re-steer itself to the first week in between.
    LaunchedEffect(pagerState, entries) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { page ->
            if (steering[0] > 0) return@collect
            entries.getOrNull(page)?.let { onFocusChange(it.weekStart) }
        }
    }
    // Focus (roll tap, week link, R14 request) → pager.
    LaunchedEffect(focusedWeekStart) {
        val index = entries.indexOfFirst { it.weekStart == focusedWeekStart }
        if (index >= 0 && index != pagerState.currentPage) {
            steering[0] += 1
            try {
                pagerState.animateScrollToPage(index)
            } finally {
                steering[0] -= 1
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekRoll(
            entries = entries,
            focusedWeekStart = focusedWeekStart,
            onFocusChange = onFocusChange,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalPager(
            state = pagerState,
            key = { entries[it].weekStart.toEpochDay() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(JourneyTags.PATH_WEEK_PAGER),
        ) { page ->
            // Entries sort ascending, so the neighbours are the pages either side; the
            // first week has no way back and the last no way on. Each link is named by
            // the week it reaches, as the roll names it.
            val previous =
                entries.getOrNull(page - 1)?.let {
                    WeekNeighbour(WeekRollBuilder.isoWeekNumber(it.weekStart)) { onFocusChange(it.weekStart) }
                }
            val next =
                entries
                    .getOrNull(
                        page + 1,
                    )?.let { WeekNeighbour(WeekRollBuilder.isoWeekNumber(it.weekStart)) { onFocusChange(it.weekStart) } }
            SpineWeek(
                entry = entries[page],
                today = today,
                soulsById = soulsById,
                paletteFor = paletteFor,
                aspect = aspect,
                onOpen = onPebbleTap,
                onRequestDelete = onPebbleDelete,
                previousWeek = previous,
                nextWeek = next,
                onCreatePebble = onCreatePebble,
                modifier = Modifier.fillMaxSize(),
                ratioOverride = ratioOverride,
            )
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

/** Delete-failure notice — a single-action AlertDialog dismissing back to the timeline. Copy shared with iOS (R15). */
@Composable
private fun DeleteErrorDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        text = {
            Text(
                text = stringResource(R.string.moment_delete_error),
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
