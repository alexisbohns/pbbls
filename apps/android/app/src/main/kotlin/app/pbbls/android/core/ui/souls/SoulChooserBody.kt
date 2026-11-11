package app.pbbls.android.core.ui.souls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph

private const val LARGE_FONT_SCALE = 1.5f
private val FAB_CLEARANCE = 96.dp
private val EDGE_FADE = 16.dp

/**
 * The chooser's list and its FAB, for the picker (pick mode) and People (browse). Tiles of the
 * usual souls, then the A–Z rows; while searching, the Add row and the matches only (mock 11).
 * The tray and the top bar belong to the host. [isLoaded] guards every "nobody" branch: a read
 * before the session is live returns an empty list, which is not proof of emptiness. A pick tap
 * plays the selection haptic through [onHaptic]; hosts pass `rememberTapHaptics { hapticsOn }`.
 * [onLongClickSoul] buzzes by [hapticsOn] (asked of every host); [itemMenu] is drawn inside each tile and row, so a
 * `DropdownMenu` there anchors on the item that was long-pressed (People's delete).
 */
@Composable
fun SoulChooserBody(
    state: SoulChooserState,
    isLoaded: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onOpenSoul: (SoulWithGlyph) -> Unit,
    onCreate: (String) -> Unit,
    onHaptic: (TapHaptic) -> Unit,
    hapticsOn: Boolean,
    modifier: Modifier = Modifier,
    onLongClickSoul: ((SoulWithGlyph, SoulChooserSlot) -> Unit)? = null,
    itemMenu: (@Composable (SoulWithGlyph, SoulChooserSlot) -> Unit)? = null,
) {
    val browse = state.mode == SoulChooserMode.Browse
    val showsSelection = !browse
    // Stable identities: a selection change must not re-run the list builder.
    val currentOpen by rememberUpdatedState(onOpenSoul)
    val currentHaptic by rememberUpdatedState(onHaptic)
    val tap: (SoulWithGlyph) -> Unit =
        remember(state, browse) {
            { soul ->
                if (browse) {
                    currentOpen(soul)
                } else {
                    currentHaptic(TapHaptic.SELECTION)
                    state.toggle(soul.id)
                }
            }
        }
    val longTap: ((SoulWithGlyph, SoulChooserSlot) -> Unit)? = onLongClickSoul
    val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 3 else 4
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.souls.isEmpty() && loadFailed -> LoadError(onRetry = onRetry)
            state.souls.isEmpty() && !isLoaded -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
                ) {
                    if (state.isSearching && state.trimmedQuery.isNotEmpty()) {
                        searchItems(state, showsSelection, tap, longTap, hapticsOn, itemMenu, onCreate)
                    } else {
                        browseItems(state, perRow, showsSelection, tap, longTap, hapticsOn, itemMenu)
                    }
                }
        }
        if (isLoaded && state.souls.isNotEmpty()) {
            // The list dissolves over the tray instead of being cut by it.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(EDGE_FADE)
                        .background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = 0f), colors.surface))),
            )
        }
        val canAdd = state.canAdd
        val canCreate = state.isSearching && canAdd
        // Lit only when the Add row is the one thing on offer; beside matches it stays pale (mock 11).
        val lit = canCreate && state.results.isEmpty()
        FloatingActionButton(
            onClick = {
                if (canCreate) onCreate(state.trimmedQuery) else state.openSearch()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(PebblesTheme.spacing.lg),
            containerColor = if (lit) colors.primary else colors.primaryContainer,
            contentColor = if (lit) colors.onPrimary else colors.onPrimaryContainer,
        ) {
            PbblsGlyphImage(glyph = PbblsGlyph.PLUS_CIRCLE, contentDescription = stringResource(R.string.souls_add_a11y))
        }
    }
}

private fun LazyListScope.searchItems(
    state: SoulChooserState,
    showsSelection: Boolean,
    tap: (SoulWithGlyph) -> Unit,
    longTap: ((SoulWithGlyph, SoulChooserSlot) -> Unit)?,
    hapticsOn: Boolean,
    itemMenu: (@Composable (SoulWithGlyph, SoulChooserSlot) -> Unit)?,
    onCreate: (String) -> Unit,
) {
    val results = state.results
    // Matches first, so the Add row never pushes the person you typed down; alone when nobody matches.
    items(results, key = { it.id }) { soul ->
        Box {
            SoulRow(
                soul = soul,
                isSelected = state.isSelected(soul.id),
                showsSelection = showsSelection,
                highlight = SoulRanking.matchRange(soul.name, state.query),
                onClick = { tap(soul) },
                onLongClick = longTap?.let { { it(soul, SoulChooserSlot.ROW) } },
                hapticsOn = hapticsOn,
            )
            itemMenu?.invoke(soul, SoulChooserSlot.ROW)
        }
    }
    if (state.canAdd) {
        item(key = "add") { SoulAddRow(name = state.trimmedQuery, onClick = { onCreate(state.trimmedQuery) }) }
    }
}

private fun LazyListScope.browseItems(
    state: SoulChooserState,
    perRow: Int,
    showsSelection: Boolean,
    tap: (SoulWithGlyph) -> Unit,
    longTap: ((SoulWithGlyph, SoulChooserSlot) -> Unit)?,
    hapticsOn: Boolean,
    itemMenu: (@Composable (SoulWithGlyph, SoulChooserSlot) -> Unit)?,
) {
    val usual = state.usual
    usual.chunked(perRow).forEachIndexed { index, line ->
        item(key = "usual-$index") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PebblesTheme.spacing.md, vertical = PebblesTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
            ) {
                line.forEach { soul ->
                    Box(modifier = Modifier.weight(1f)) {
                        SoulTile(
                            soul = soul,
                            isSelected = state.isSelected(soul.id),
                            showsSelection = showsSelection,
                            onClick = { tap(soul) },
                            onLongClick = longTap?.let { { it(soul, SoulChooserSlot.TILE) } },
                            hapticsOn = hapticsOn,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        itemMenu?.invoke(soul, SoulChooserSlot.TILE)
                    }
                }
                repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    state.sections.forEach { section ->
        item(key = "header-${section.letter}") {
            Text(
                text = section.letter,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = PebblesTheme.spacing.lg,
                        top = PebblesTheme.spacing.sm,
                        bottom = PebblesTheme.spacing.xs,
                    ),
            )
        }
        items(section.souls, key = { it.id }) { soul ->
            Box {
                SoulRow(
                    soul = soul,
                    isSelected = state.isSelected(soul.id),
                    showsSelection = showsSelection,
                    highlight = null,
                    onClick = { tap(soul) },
                    onLongClick = longTap?.let { { it(soul, SoulChooserSlot.ROW) } },
                    hapticsOn = hapticsOn,
                )
                itemMenu?.invoke(soul, SoulChooserSlot.ROW)
            }
        }
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PebblesTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.souls_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.profile_retry)) }
    }
}
