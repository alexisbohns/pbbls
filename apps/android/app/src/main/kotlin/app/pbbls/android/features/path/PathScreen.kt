package app.pbbls.android.features.path

import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.components.NewPebbleButton
import app.pbbls.android.features.path.components.WeekHeader
import app.pbbls.android.features.path.components.WeekPebbleList
import app.pbbls.android.features.path.components.WeekRoll
import app.pbbls.android.features.path.create.CreatePebbleScreen
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPathService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private const val TAG = "path"

/**
 * The read-only Path timeline — the authenticated landing surface (issue
 * #531, `PathView.swift` analog minus create/detail/delete/stats). Loads
 * every pebble once via `path_pebbles()`, groups by ISO week, and pages the
 * body by week. The temporary sign-out stays until Profile exists.
 */
@Composable
fun PathScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pathService = LocalPathService.current
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    val today = remember { LocalDate.now() }
    var entries by remember { mutableStateOf<List<WeekRollEntry>>(emptyList()) }
    var focusedWeekStart by remember { mutableStateOf<LocalDate?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var didLoadFail by remember { mutableStateOf(false) }

    val writeService = LocalPebbleWriteService.current
    val scope = rememberCoroutineScope()
    var selectedPebbleId by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<Pebble?>(null) }
    var deleteError by remember { mutableStateOf(false) }
    var isPresentingCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val pebbles = pathService.loadPathPebbles()
            val built = WeekRollBuilder.build(pebbles, ZoneId.systemDefault(), today)
            entries = built
            focusedWeekStart = refocusedWeekStart(built, focusedWeekStart, today)
            didLoadFail = false
        } catch (e: Exception) {
            Log.e(TAG, "path_pebbles load failed", e)
            didLoadFail = true
        } finally {
            isLoading = false
        }
    }

    // Hoisted so the delete flow (and, later, create/edit in C/D) can refresh the
    // timeline without re-triggering the first-load spinner: isLoading is left
    // untouched, mirroring iOS PathView.load() after the initial fetch.
    val reload: () -> Unit = {
        scope.launch {
            try {
                val pebbles = pathService.loadPathPebbles()
                val built = WeekRollBuilder.build(pebbles, ZoneId.systemDefault(), today)
                entries = built
                focusedWeekStart = refocusedWeekStart(built, focusedWeekStart, today)
            } catch (e: Exception) {
                Log.e(TAG, "path reload failed", e)
            }
        }
    }

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
            val focused = focusedWeekStart
            when {
                isLoading ->
                    CircularProgressIndicator(
                        color = accent.primary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                didLoadFail || focused == null ->
                    PebblesText(
                        text = stringResource(R.string.path_load_error),
                        style = PebblesTypography.body,
                        color = system.secondary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else ->
                    PathContent(
                        entries = entries,
                        initialWeekStart = focused,
                        focusedWeekStart = focused,
                        today = today,
                        onFocusChange = { focusedWeekStart = it },
                        paletteFor = { pebble -> pebble.emotion?.let { palettes.palette(it.id) } },
                        onSignOut = onSignOut,
                        onPebbleTap = { pebble -> selectedPebbleId = pebble.id },
                        onPebbleDelete = { pebble -> pendingDeletion = pebble },
                        onCreatePebble = { isPresentingCreate = true },
                    )
            }
        }

        // Full-screen detail cover (self-applies safeDrawingPadding, so it lives in
        // the OUTER Box) — the fullScreenCover analog (D5).
        val detailId = selectedPebbleId
        if (detailId != null) {
            PebbleDetailScreen(
                pebbleId = detailId,
                onDismiss = { selectedPebbleId = null },
                onEditRequested = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Full-screen create cover — sibling of the detail cover, also in the
        // OUTER (unpadded) Box since it self-applies safeDrawingPadding/imePadding
        // (C, the fullScreenCover analog D5). On success it reveals the new pebble
        // through the detail cover and reloads the timeline.
        if (isPresentingCreate) {
            CreatePebbleScreen(
                onCreated = { newId ->
                    isPresentingCreate = false
                    selectedPebbleId = newId
                    reload()
                },
                onCancel = { isPresentingCreate = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val target = pendingDeletion
        if (target != null) {
            DeleteConfirmDialog(
                pebbleName = target.name,
                onConfirm = {
                    pendingDeletion = null
                    scope.launch {
                        try {
                            writeService.delete(target.id)
                            if (selectedPebbleId == target.id) selectedPebbleId = null
                            reload()
                        } catch (e: Exception) {
                            Log.e(TAG, "delete pebble failed", e)
                            deleteError = true
                        }
                    }
                },
                onDismiss = { pendingDeletion = null },
            )
        }
        if (deleteError) DeleteErrorDialog(onDismiss = { deleteError = false })
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
    onSignOut: () -> Unit,
    onPebbleTap: (Pebble) -> Unit = {},
    onPebbleDelete: (Pebble) -> Unit = {},
    onCreatePebble: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
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
        // Pinned "New pebble" entry — the PathView.safeAreaInset(.bottom) analog.
        NewPebbleButton(
            onTap = onCreatePebble,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // Temporary affordance until Profile exists — re-testing the funnel
        // on device requires a way back to Welcome.
        TextButton(
            onClick = onSignOut,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            PebblesText(
                text = stringResource(R.string.path_sign_out),
                style = PebblesTypography.buttonLabel,
                color = accent.primary,
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
