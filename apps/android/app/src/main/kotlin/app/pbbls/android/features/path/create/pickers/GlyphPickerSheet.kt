package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.designsystem.PebblesText
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTypography
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.carve.GlyphCarveViewModel
import app.pbbls.android.features.glyph.store.GlyphSwapPanel
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.features.glyph.store.GlyphTabBar

/**
 * The glyph picker's content-swap state, hoisted out of the picker so both
 * callers can unwind it.
 *
 * The sheet unwinds on a dismiss gesture (back returns to the grid before it
 * closes the sheet); the record flow's glyph step unwinds on the system back
 * before stepping backwards. Without the hoist, the flow would have no way to
 * ask "is a swap panel open?" — and a nested panel that outlives its step is
 * exactly the trap the iOS port hit, where `GlyphDetailDrawer` survived onto the
 * next step because only the sheet's dismissal had ever taken it down.
 */
class GlyphPickerState {
    var isCarving: Boolean by mutableStateOf(false)
        internal set

    var buying: GlyphGridItem? by mutableStateOf(null)
        internal set

    /**
     * Close the topmost content swap. Returns true when one was open (so the
     * caller keeps its own surface), false when the grid is already showing.
     */
    fun unwind(): Boolean =
        when {
            isCarving -> {
                isCarving = false
                true
            }
            buying != null -> {
                buying = null
                true
            }
            else -> false
        }

    /** Back to the grid — after a select, or after a purchase completes. */
    internal fun reset() {
        isCarving = false
        buying = null
    }
}

@Composable
fun rememberGlyphPickerState(): GlyphPickerState = remember { GlyphPickerState() }

/**
 * The tabbed glyph picker — the #549 harmonization (M43 D10): the same
 * Mine / Owned / Commu tabs as the store inside the caller's single
 * `ModalBottomSheet` level, with inline buy and a carve row. The API is the
 * M39 drop-in (`currentGlyphId` + `onSelected`), so the three call sites
 * (pebble form, soul form, Settings) are untouched.
 *
 * D5 adaptations (named): the swap panel and the carve studio open as
 * CONTENT SWAPS inside this sheet rather than stacked sheets/covers — back
 * (or a dismiss gesture) returns to the grid first. Mine keeps system
 * glyphs (D7); Commu client-filters `!owned` on top of the server `.neq`
 * (owned community glyphs live under Owned — both filters load-bearing).
 *
 * The body itself lives in [GlyphPickerContent] (M58 D5) so the record flow's
 * glyph step renders the same grid inline, under the flow's own chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphPickerSheet(
    currentGlyphId: String?,
    onDismiss: () -> Unit,
    onSelected: (Glyph) -> Unit,
) {
    val state = rememberGlyphPickerState()
    ModalBottomSheet(
        // Content swaps unwind before the sheet itself dismisses (D5).
        onDismissRequest = { if (!state.unwind()) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        GlyphPickerContent(
            currentGlyphId = currentGlyphId,
            onSelected = onSelected,
            state = state,
        )
    }
}

/**
 * The picker body: tabs, grid, inline buy, and the carve entry point.
 * Presentation plus its own loading — no sheet, no dismissal (M58 D5).
 *
 * Callers differ in commit semantics and own that difference: the sheet
 * dismisses on select, the flow's step advances. [state] is hoisted so either
 * can unwind a content swap; whichever swap is open closes itself before
 * [onSelected] fires, so a panel can never outlive the surface that opened it.
 */
@Composable
fun GlyphPickerContent(
    currentGlyphId: String?,
    onSelected: (Glyph) -> Unit,
    modifier: Modifier = Modifier,
    state: GlyphPickerState = rememberGlyphPickerState(),
    viewModel: GlyphPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = PebblesTheme.colors.accent

    // Every select leaves the picker showing its grid: the effect resets the
    // content swap before handing the glyph to the caller, so a selection
    // never replays across a rotation the way holding it in state would.
    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is GlyphPickerEffect.Selected -> {
                state.reset()
                onSelected(effect.glyph)
            }
        }
    }

    // The carve surface owns its strokes in a ViewModel now (#849), and this
    // picker closes that surface by flipping `isCarving` — through `unwind()`
    // for a dismiss gesture, a scrim tap or the record flow stepping back —
    // without ever reaching the screen's own cancel path. This is the same
    // instance `GlyphCarveScreen` resolves, so releasing it here is what keeps
    // an abandoned drawing from surfacing under the next carve. Idempotent
    // after a save or a discard.
    //
    // Still needed (#852): `GlyphCarve` is a nav entry for the store's "+" but
    // this sheet still hosts the carve studio as an inline content swap, so
    // the ViewModel above is not released by leaving a destination the way the
    // store's is. See `GlyphCarveViewModelTest.reset clears a carve abandoned
    // by its host`.
    val carveViewModel: GlyphCarveViewModel = hiltViewModel()
    LaunchedEffect(state.isCarving) {
        if (!state.isCarving) carveViewModel.reset()
    }

    val buyingItem = state.buying
    when {
        state.isCarving ->
            GlyphCarveScreen(
                onSaved = { glyph -> viewModel.onCarved(glyph) },
                onCancel = { state.isCarving = false },
                modifier = modifier.fillMaxWidth().heightIn(min = 200.dp),
            )

        buyingItem != null ->
            Column(modifier.fillMaxWidth()) {
                TextButton(onClick = { state.buying = null }) {
                    PebblesText(
                        text = stringResource(R.string.action_cancel),
                        style = PebblesTypography.buttonLabel,
                        color = accent.primary,
                    )
                }
                GlyphSwapPanel(
                    item = buyingItem,
                    balance = (uiState as? GlyphPickerUiState.Content)?.karma ?: 0,
                    market = viewModel.market,
                    // The balance is a record of the purchase, so it runs inside
                    // the uncancellable section and survives the sheet closing.
                    onRecorded = { result -> viewModel.onPurchaseRecorded(result) },
                    onSwapped = {
                        // First successful swap selects and hands control back to
                        // the call site (iOS parity). Selecting closes the panel
                        // first: the panel flips to its owned state rather than
                        // dismissing itself, so a caller that does not dismiss
                        // would otherwise be left holding it.
                        //
                        // Deliberately OUTSIDE the uncancellable section: this
                        // writes into the form that opened the picker, and a
                        // user who dismissed the sheet mid-buy must not find
                        // their profile glyph silently changed.
                        viewModel.selectGlyph(buyingItem.glyph)
                    },
                )
            }

        else ->
            Column(modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                when (val current = uiState) {
                    GlyphPickerUiState.Loading ->
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = accent.primary)
                        }
                    is GlyphPickerUiState.Error -> GlyphLoadError(onRetry = viewModel::retry)
                    is GlyphPickerUiState.Content ->
                        GlyphPickerGrid(
                            items = current.items,
                            currentGlyphId = currentGlyphId,
                            showCarveRow = current.tab == GlyphTab.MINE,
                            onCarve = { state.isCarving = true },
                            onSelect = { item ->
                                if (current.tab == GlyphTab.COMMU) {
                                    state.buying = item
                                } else {
                                    viewModel.selectGlyph(item.glyph)
                                }
                            },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                }
                GlyphTabBar(
                    selection = uiState.tab,
                    onSelect = viewModel::onSelectTab,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
    }
}

/** Whichever tab the picker is on, loading, failed or not — the bar must not lie. */
private val GlyphPickerUiState.tab: GlyphTab
    get() =
        when (this) {
            is GlyphPickerUiState.Content -> tab
            is GlyphPickerUiState.Error -> tab
            GlyphPickerUiState.Loading -> GlyphTab.MINE
        }

/**
 * The picker grid — 3-per-row cells (selection carried by glyph color, #459),
 * price badges on listed community glyphs, and the carve tile leading the
 * Mine tab (doubling as its empty state). `internal`-equivalent public for
 * the screenshot gallery.
 */
@Composable
fun GlyphPickerGrid(
    items: List<GlyphGridItem>,
    currentGlyphId: String?,
    showCarveRow: Boolean,
    onCarve: () -> Unit,
    onSelect: (GlyphGridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PebblesText(
            text = stringResource(R.string.create_glyph_title),
            style = PebblesTypography.cardHeading,
            color = system.secondary,
        )
        val cells: List<PickerCell> =
            buildList {
                if (showCarveRow) add(PickerCell.Carve)
                items.forEach { add(PickerCell.Item(it)) }
            }
        cells.chunked(3).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowCells.forEach { cell ->
                    Box(Modifier.weight(1f)) {
                        when (cell) {
                            is PickerCell.Carve -> CarveTile(onCarve)
                            is PickerCell.Item ->
                                PickerGlyphTile(
                                    item = cell.item,
                                    isSelected = cell.item.glyph.id == currentGlyphId,
                                    onTap = { onSelect(cell.item) },
                                )
                        }
                    }
                }
                repeat(3 - rowCells.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private sealed interface PickerCell {
    data object Carve : PickerCell

    data class Item(
        val item: GlyphGridItem,
    ) : PickerCell
}

@Composable
private fun CarveTile(onCarve: () -> Unit) {
    val system = PebblesTheme.colors.system
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onCarve)
                .padding(8.dp),
    ) {
        GlyphView(case = GlyphViewCase.CREATE, side = 72.dp)
        PebblesText(
            text = stringResource(R.string.carve_title),
            style = PebblesTypography.meta,
            color = system.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PickerGlyphTile(
    item: GlyphGridItem,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTap)
                .padding(8.dp),
    ) {
        GlyphView(
            case = if (isSelected) GlyphViewCase.SELECTED else GlyphViewCase.DEFAULT,
            strokes = item.glyph.strokes,
            viewBox = item.glyph.viewBox,
            side = 72.dp,
        )
        if (item.price > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = system.muted,
                    modifier = Modifier.size(11.dp),
                )
                PebblesText(
                    text = item.price.toString(),
                    style = PebblesTypography.meta,
                    color = system.muted,
                )
            }
        }
    }
}

@Composable
private fun GlyphLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PebblesText(
            text = stringResource(R.string.create_glyph_load_error),
            style = PebblesTypography.callout,
            color = system.secondary,
        )
        TextButton(onClick = onRetry) {
            PebblesText(
                text = stringResource(R.string.pebble_detail_retry),
                style = PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}
