package app.pbbls.android.core.ui.souls

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PebblesTheme
import kotlinx.coroutines.launch

private const val TAG = "SoulChooserSheet"

/**
 * The souls chooser as a sheet (souls spec S5): the record flow's More tile opens it full height
 * ([startsPartial] false, no handle, so it reads as mock 10), Edit pebble opens it at half height
 * with the stock handle, expandable to full (screen 06). Done hands [SoulChooserState.selection] to
 * [onConfirm]; a drag down, the scrim and back call only [onDismiss], so the previous selection
 * stands (S2). Searching expands a half sheet, so the keyboard has room.
 *
 * Reads the cached souls from [LocalReferenceDataService] and refreshes them once on open, because
 * the cache refreshes only on soul mutations and the counts would lag every pebble recorded since
 * launch (#1370). The Add row creates through `createSoul` then [onAchievementCheck]. That write
 * runs in this sheet's composition scope: closing the sheet while it is in flight cancels the
 * request, though the insert may already have reached the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoulChooserSheet(
    title: String,
    currentSelection: List<String>,
    allowsSkip: Boolean,
    startsPartial: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    hapticsOn: Boolean,
    onAchievementCheck: () -> Unit = {},
) {
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val state =
        rememberSoulChooserState(
            mode = SoulChooserMode.Pick(allowsSkip),
            souls = refs.souls,
            isLoaded = refs.hasLoaded,
            selection = currentSelection,
        )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !startsPartial)

    LaunchedEffect(Unit) { refs.refreshSouls() }
    // The field and the keyboard need the height; a half sheet would leave them a sliver.
    LaunchedEffect(state.isSearching) { if (state.isSearching && startsPartial) sheetState.expand() }

    // Back closes the search first, then the sheet (the sheet's own back is onDismiss).
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = state.isSearching) { state.closeSearch() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = if (startsPartial) ({ BottomSheetDefaults.DragHandle() }) else null,
    ) {
        // A half sheet is laid out at full height and offset down, so its bottom is off screen:
        // the content keeps its tray above the screen edge by the amount that hangs below it.
        var bottom by remember { mutableIntStateOf(0) }
        val windowHeight = LocalWindowInfo.current.containerSize.height
        val safeBottom = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
        val hidden = with(LocalDensity.current) { (bottom - (windowHeight - safeBottom)).coerceAtLeast(0).toDp() }
        SoulChooserSheetContent(
            state = state,
            title = title,
            isLoaded = refs.hasLoaded,
            // ReferenceDataServicing swallows its own failures and exposes no failed state (S7).
            loadFailed = false,
            hapticsOn = hapticsOn,
            onBack = onDismiss,
            onDone = { onConfirm(state.selection) },
            onCreate = { name ->
                scope.launch {
                    val created = refs.createSoul(name)
                    if (created != null) {
                        onAchievementCheck()
                        state.didCreate(created)
                    } else {
                        Log.e(TAG, "createSoul returned null")
                        state.createErrorRes = R.string.soul_save_error
                    }
                }
            },
            onRetry = { scope.launch { refs.refreshSouls() } },
            modifier =
                Modifier
                    .fillMaxHeight()
                    .imePadding()
                    .onGloballyPositioned { bottom = it.boundsInWindow().bottom.toInt() },
            hiddenBottom = hidden,
        )
    }
}

/**
 * The sheet's inside without the sheet, so the previews render it (a `ModalBottomSheet` does not
 * render there): the top bar (or the search field), the chooser, and the tray. [hiddenBottom] is
 * how much of the content hangs below the screen edge on a half sheet.
 */
@Composable
fun SoulChooserSheetContent(
    state: SoulChooserState,
    title: String,
    isLoaded: Boolean,
    loadFailed: Boolean,
    hapticsOn: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onCreate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hiddenBottom: Dp = 0.dp,
    onHaptic: (TapHaptic) -> Unit = rememberTapHaptics { hapticsOn },
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = hiddenBottom)) {
        if (state.isSearching) {
            SoulChooserSearchField(
                state = state,
                onClose = state::closeSearch,
                modifier = Modifier.padding(top = PebblesTheme.spacing.sm, bottom = PebblesTheme.spacing.sm),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PebblesTheme.spacing.sm, vertical = PebblesTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.onboarding_back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                SoulChooserSearchButton(state = state)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            SoulChooserBody(
                state = state,
                isLoaded = isLoaded,
                loadFailed = loadFailed,
                onRetry = onRetry,
                onOpenSoul = {},
                onCreate = onCreate,
                onHaptic = onHaptic,
                hapticsOn = hapticsOn,
            )
        }
        SoulChooserTray(state = state, onDone = onDone, onHaptic = onHaptic)
    }
}
