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
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.components.NewPebbleFab
import app.pbbls.android.features.path.spine.SpineWeek
import app.pbbls.android.features.path.spine.WeekNeighbour
import app.pbbls.android.features.path.strip.PathPage
import app.pbbls.android.features.path.strip.PathPages
import app.pbbls.android.features.path.strip.PathStrip
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.WrapState
import app.pbbls.android.features.path.wrap.intro.IntroLedger
import app.pbbls.android.features.path.wrap.intro.rememberIntroVibrator
import app.pbbls.android.features.path.wrap.month.MonthIntro
import app.pbbls.android.features.path.wrap.month.MonthPage
import app.pbbls.android.features.path.wrap.month.MonthPagePlaceholder
import app.pbbls.android.features.path.wrap.shown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.YearMonth

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
    onOpenDetail: (String, CardFace) -> Unit,
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
                    val paletteFor: (PathMoment) -> EmotionPalette? = { moment -> moment.pebble?.emotion?.let { palettes.palette(it.id) } }
                    val references = LocalReferenceDataService.current
                    val families =
                        remember(palettes.byEmotionId) { EmotionFamilies.of(palettes.byEmotionId.values).associateBy { it.slug } }
                    val domains = remember(references.domains) { references.domains.associateBy { it.id } }
                    val moments = remember(content.entries) { content.entries.flatMap { it.moments }.associateBy { it.id } }
                    val lookups =
                        remember(moments, content.soulsById, families, domains, palettes) {
                            WrapLookups(
                                moments::get,
                                content.soulsById::get,
                                families::get,
                                domains::get,
                                paletteFor,
                                palettes.byEmotionId::get,
                            )
                        }
                    PathContent(
                        entries = content.entries,
                        stripItems = content.stripItems,
                        focus = content.focus,
                        weekWraps = content.weekWraps,
                        monthWraps = content.monthWraps,
                        dayView = content.dayView,
                        onDayView = viewModel::onDayView,
                        lookups = lookups,
                        today = content.today,
                        soulsById = content.soulsById,
                        onFocusChange = viewModel::onFocus,
                        paletteFor = paletteFor,
                        aspect = viewModel.snapAspect,
                        introLedger = viewModel.introLedger,
                        onPebbleTap = { moment, face -> onOpenDetail(moment.id, face) },
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
 * The strip, then the pager (#1207): one page per focusable strip item, a
 * week on the Spine (#1122) or a month. Focus has a single source of truth
 * (the caller's [focus]); the pager and the strip both follow it: a swipe
 * reports the new page through [onFocusChange], a strip tap or a week link
 * changes focus and the pager moves to it (snapping under reduced motion).
 * Scrolling to the already-current page is a no-op, so the two effects settle.
 * A swipe that settles on another page, and a strip tap on another page, play
 * the selection tick once (#1242 D24); a page the focus steered to does not
 * tick again as it settles.
 *
 * @param entries the feed's weeks; a week page the feed does not hold renders empty.
 * @param stripItems the strip as drawn ([PathPages.items]); its focusable items are the pages.
 * @param weekWraps each week page's wrap; a week absent here shows its line alone.
 * @param lookups what the wraps' ids point at.
 * @param monthWraps each month page's wrap; a month absent here shows its name alone.
 * @param dayView the month's day-dot view, switched in its sheet through [onDayView].
 * @param soulsById the souls the persons rows and people lines draw glyphs
 *   from; a soul absent from it is skipped.
 * @param aspect the photo-ratio probe the cards read; null (previews) keeps
 *   every field square unless [ratioOverride] answers.
 * @param introLedger the month intros played this launch (#1242 D20); with
 *   one, a month page the policy allows is dealt as a deck and plays its
 *   intro while it is the settled page. Null (previews): the plain page.
 * @param ratioOverride preview-only: a primed ratio by storage prefix, so a
 *   render never waits on a probe. Production passes nothing.
 */
@Composable
fun PathContent(
    entries: List<WeekRollEntry>,
    stripItems: List<StripItem>,
    focus: PathPage,
    weekWraps: Map<LocalDate, WrapState<WeekWrap>>,
    lookups: WrapLookups,
    monthWraps: Map<YearMonth, WrapState<MonthWrap>> = emptyMap(),
    dayView: DayView = DayView.CIRCLE,
    onDayView: (DayView) -> Unit = {},
    today: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    onFocusChange: (PathPage) -> Unit,
    paletteFor: (PathMoment) -> EmotionPalette?,
    modifier: Modifier = Modifier,
    aspect: SnapAspectCache? = null,
    introLedger: IntroLedger? = null,
    onPebbleTap: (PathMoment, CardFace) -> Unit = { _, _ -> },
    onPebbleDelete: (PathMoment) -> Unit = {},
    onCreatePebble: () -> Unit = {},
    ratioOverride: ((String) -> Float?)? = null,
) {
    val pages = remember(stripItems) { PathPages.pages(stripItems) }
    val weeks = remember(entries) { entries.associateBy { it.weekStart } }
    val reduceMotion = rememberReduceMotion()
    val pagerState = rememberPagerState(initialPage = pages.indexOf(focus).coerceAtLeast(0)) { pages.size }
    val introHaptics = if (introLedger != null) rememberIntroVibrator() else null
    val intro = remember(introLedger, introHaptics) { introLedger?.let { MonthIntro(it, introHaptics) } }

    // Programmatic scrolls in flight. A counter, not a flag: a new focus
    // cancels the running scroll, whose cleanup can land after the next one
    // has started. Not snapshot state: only the collector below reads it.
    val steering = remember { intArrayOf(0) }
    val tapHaptics = rememberTapHaptics()
    // The page a focus change last steered the pager to (a strip tap, a week
    // link, a focus request): it ticked at the tap if at all, so its settle
    // does not tick again. Not snapshot state: only the collector reads it.
    val steeredTo = remember { intArrayOf(-1) }

    // Swipe → focus. Keyed on the pages so a reload re-subscribes; the page in
    // place at (re)subscription is dropped, because a reload is not a page
    // change and reporting it would overwrite a requested focus (R14). Pages
    // the pager passes while it is being steered are skipped too, or a jump
    // several pages away would re-steer itself to the first page in between.
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { page ->
            if (steering[0] > 0) return@collect
            pages.getOrNull(page)?.let(onFocusChange)
        }
    }
    // A swipe that settles on another page plays the strip's selection tick
    // (#1242 D24; iOS `SpinePager`). The page in place at (re)subscription is
    // dropped, as above: a reload is not a page change. Any settle clears the
    // steer, so a steer a drag cut short never mutes a later swipe.
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            val steered = page == steeredTo[0]
            steeredTo[0] = -1
            if (!steered) tapHaptics(TapHaptic.SELECTION)
        }
    }
    // Focus (strip tap, week link, R14 request) → pager.
    LaunchedEffect(focus, pages) {
        val index = pages.indexOf(focus)
        if (index >= 0 && index != pagerState.currentPage) {
            steering[0] += 1
            steeredTo[0] = index
            try {
                if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
            } catch (e: CancellationException) {
                // A drag or a newer focus cut the steer short: it no longer mutes a swipe onto its page.
                if (steeredTo[0] == index) steeredTo[0] = -1
                throw e
            } finally {
                steering[0] -= 1
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PathStrip(
            items = stripItems,
            focus = focus,
            onFocus = { page ->
                // A tap on another page ticks (D24; iOS `PathStrip`); the focused one is already there.
                if (page != focus) tapHaptics(TapHaptic.SELECTION)
                onFocusChange(page)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalPager(
            state = pagerState,
            key = { pages[it].toString() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(JourneyTags.PATH_WEEK_PAGER),
        ) { index ->
            when (val page = pages[index]) {
                is PathPage.Week -> {
                    // The mouth reaches the nearest week after, past any month between; it is
                    // named by its ISO week, as the strip names it.
                    val next =
                        (index + 1..pages.lastIndex)
                            .firstNotNullOfOrNull { pages.getOrNull(it) as? PathPage.Week }
                            ?.let { week -> WeekNeighbour(WeekRollBuilder.isoWeekNumber(week.weekStart)) { onFocusChange(week) } }
                    SpineWeek(
                        entry = weeks[page.weekStart] ?: WeekRollEntry(page.weekStart, emptyList()),
                        today = today,
                        soulsById = soulsById,
                        paletteFor = paletteFor,
                        aspect = aspect,
                        onOpen = onPebbleTap,
                        onRequestDelete = onPebbleDelete,
                        wrap = weekWraps[page.weekStart]?.shown,
                        lookups = lookups,
                        nextWeek = next,
                        onCreatePebble = onCreatePebble,
                        modifier = Modifier.fillMaxSize(),
                        ratioOverride = ratioOverride,
                    )
                }
                is PathPage.Month -> {
                    val wrap = monthWraps[page.month]?.shown
                    if (wrap == null) {
                        MonthPagePlaceholder(page.month, Modifier.fillMaxSize())
                    } else {
                        MonthPage(
                            wrap = wrap,
                            lookups = lookups,
                            dayView = dayView,
                            onDayView = onDayView,
                            onWeek = { onFocusChange(PathPage.Week(it)) },
                            onOpen = { moment -> onPebbleTap(moment, CardFace.RECTO) },
                            modifier = Modifier.fillMaxSize(),
                            intro = intro,
                            focused = pagerState.settledPage == index,
                            aspect = aspect,
                        )
                    }
                }
            }
        }
    }
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
