package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PebblesTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The souls chooser as a sheet (souls spec S5): the record flow's More tile opens it full height
 * ([startsPartial] false, no handle, so it reads as mock 10), Edit pebble opens it at half height
 * with the stock handle, expandable to full (screen 06). Done hands [SoulChooserState.selection] to
 * [onConfirm]; a drag down, the scrim and back call only [onDismiss], so the previous selection
 * stands (S2). Searching expands a half sheet, so the keyboard has room. Back closes the search,
 * then the sheet.
 *
 * Reads the cached souls from [LocalReferenceDataService] and refreshes them once on open, because
 * the cache refreshes only on soul mutations and the counts would lag every pebble recorded since
 * launch (#1370). The Add row goes through [rememberSoulCreate]: one write at a time, finished even if
 * the sheet closes first, then [onAchievementCheck].
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
    val onCreate = rememberSoulCreate(state, refs, onAchievementCheck)
    val hide: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    LaunchedEffect(Unit) { refs.refreshSouls() }
    // The field and the keyboard need the height; a half sheet would leave them a sliver.
    LaunchedEffect(state.isSearching) { if (state.isSearching && startsPartial) sheetState.expand() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = if (startsPartial) ({ BottomSheetDefaults.DragHandle() }) else null,
        // The sheet is its own window with its own back dispatcher, and its stock back collapses an
        // expanded half sheet before it dismisses. Back is ours instead: it closes the search, then
        // the sheet, whatever the height.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        // The status bar and the keyboard; the gesture bar is the tray's, so its tint reaches the edge.
        contentWindowInsets = {
            WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars.exclude(WindowInsets.ime))
        },
    ) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { if (state.isSearching) state.closeSearch() else hide() }
        SoulChooserSheetContent(
            state = state,
            title = title,
            isLoaded = refs.hasLoaded,
            loadFailed = refs.loadFailed,
            hapticsOn = hapticsOn,
            onBack = hide,
            onDone = { onConfirm(state.selection) },
            onCreate = onCreate,
            onRetry = { scope.launch { refs.load() } },
            modifier = if (startsPartial) Modifier.keepAboveScreenEdge(sheetState) else Modifier.fillMaxHeight(),
        )
    }
}

/**
 * A half sheet is laid out at full height and offset down by the sheet's own offset, so the part
 * of the content below the screen edge is that offset. The content is given the height that stays
 * on screen instead, while this node still reports the full height, so the sheet's anchors never
 * move. Read in the layout phase: a drag relays out and recomposes nothing. The offset is read as
 * far as the half anchor only, so a sheet sliding in or out keeps its settled layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.keepAboveScreenEdge(sheetState: SheetState): Modifier = this.then(KeepAboveScreenEdge(sheetState))

@OptIn(ExperimentalMaterial3Api::class)
private class KeepAboveScreenEdge(
    private val sheetState: SheetState,
) : LayoutModifier {
    // The offset the half anchor settles at, once seen; before that, half the height is the guess.
    private var halfOffset = Float.NaN

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val full = constraints.maxHeight
        val offset =
            try {
                sheetState.requireOffset()
            } catch (_: IllegalStateException) {
                // The anchors do not exist yet: first frame.
                0f
            }
        if (sheetState.currentValue == SheetValue.PartiallyExpanded && sheetState.targetValue == SheetValue.PartiallyExpanded) {
            halfOffset = offset
        }
        val cap = if (halfOffset.isNaN()) full / 2f else halfOffset
        val hidden = offset.coerceIn(0f, cap).roundToInt().coerceAtMost(full)
        val height = full - hidden
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        return layout(placeable.width, full) { placeable.place(0, 0) }
    }

    override fun equals(other: Any?): Boolean = other is KeepAboveScreenEdge && other.sheetState == sheetState

    override fun hashCode(): Int = sheetState.hashCode()
}

/**
 * The sheet's inside without the sheet, so the previews render it (a `ModalBottomSheet` does not
 * render there): the top bar (or the search field), the chooser, and the tray. */
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
    onHaptic: (TapHaptic) -> Unit = rememberTapHaptics { hapticsOn },
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
