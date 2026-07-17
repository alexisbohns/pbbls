package app.pbbls.android.features.path.create.pickers

import android.util.Log
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.services.LocalGlyphMarketService
import app.pbbls.android.features.glyph.store.GlyphSwapPanel
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.features.glyph.store.GlyphTabBar
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

private const val TAG = "glyph-picker"

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphPickerSheet(
    currentGlyphId: String?,
    onDismiss: () -> Unit,
    onSelected: (Glyph) -> Unit,
) {
    val market = LocalGlyphMarketService.current
    val stats = LocalPathStatsService.current
    val accent = PebblesTheme.colors.accent

    var tab by remember { mutableStateOf(GlyphTab.MINE) }
    val itemsByTab = remember { mutableStateMapOf<GlyphTab, List<GlyphGridItem>>() }
    var isLoading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var buying by remember { mutableStateOf<GlyphGridItem?>(null) }
    var isCarving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { stats.load() }
    LaunchedEffect(tab, reloadToken) {
        isLoading = true
        loadFailed = false
        try {
            itemsByTab[tab] =
                when (tab) {
                    GlyphTab.MINE -> market.listMine()
                    GlyphTab.OWNED -> market.listOwned()
                    GlyphTab.COMMU -> market.listCommunity().filter { !it.owned }
                }
        } catch (e: Exception) {
            Log.e(TAG, "glyph picker tab load failed: $tab", e)
            if (itemsByTab[tab].isNullOrEmpty()) loadFailed = true
        } finally {
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Content swaps unwind before the sheet itself dismisses (D5).
            when {
                isCarving -> isCarving = false
                buying != null -> buying = null
                else -> onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val buyingItem = buying
        when {
            isCarving ->
                GlyphCarveScreen(
                    onSaved = { glyph ->
                        isCarving = false
                        val fresh =
                            GlyphGridItem(glyph = glyph, price = 0, owned = false, createdAt = null, acquiredAt = null)
                        itemsByTab[GlyphTab.MINE] = listOf(fresh) + itemsByTab[GlyphTab.MINE].orEmpty()
                        onSelected(glyph)
                    },
                    onCancel = { isCarving = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                )

            buyingItem != null ->
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { buying = null }) {
                        PebblesText(
                            text = stringResource(R.string.action_cancel),
                            style = PebblesTypography.buttonLabel,
                            color = accent.primary,
                        )
                    }
                    GlyphSwapPanel(
                        item = buyingItem,
                        balance = stats.karma ?: 0,
                        onSwapped = { result ->
                            stats.applyKarmaBalance(result.balance)
                            // First successful swap selects and hands control
                            // back to the call site (iOS parity).
                            onSelected(buyingItem.glyph)
                        },
                    )
                }

            else ->
                Column(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
                    when {
                        isLoading && itemsByTab[tab].isNullOrEmpty() ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = accent.primary)
                            }
                        loadFailed -> GlyphLoadError(onRetry = { reloadToken++ })
                        else ->
                            GlyphPickerGrid(
                                items = itemsByTab[tab].orEmpty(),
                                currentGlyphId = currentGlyphId,
                                showCarveRow = tab == GlyphTab.MINE,
                                onCarve = { isCarving = true },
                                onSelect = { item ->
                                    if (tab == GlyphTab.COMMU) buying = item else onSelected(item.glyph)
                                },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                    }
                    GlyphTabBar(
                        selection = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
        }
    }
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
