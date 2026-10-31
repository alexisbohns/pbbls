package app.pbbls.android.features.home.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsLayout
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PageShapes
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.StoneGeometry
import app.pbbls.android.core.designsystem.stone.StoneOptions
import kotlin.math.max
import kotlin.math.roundToInt

/** The gap between two pieces or tiles: the stones' own gutter, so both styles line up. */
private val GUTTER_DP = StoneOptions().gutter
private val GUTTER = GUTTER_DP.dp

/** The row's least height (the mock's 100 px rock); text grows it (D18). */
private val ROW_MIN_HEIGHT = 100.dp

/** The camera piece while a camera moment saves (D6), fill and content alike (iOS). */
private const val DIMMED = 0.6f

/** The bento's square: 45 % of the strip (iOS `ShortcutsBentoLayout`, 160 of 356 pt). */
private const val LEAD_SHARE = 0.45f

/** Above this font scale three pieces side by side no longer hold their labels: they stack (D18). */
private const val STACK_ABOVE_FONT_SCALE = 1.5f

private val LEAD_ICON = 40.dp

/**
 * Home's three quick actions (#1376, #1383 D6; iOS `HomeShortcuts`): camera, quick moment and
 * new pebble, in `primaryContainer` with `onPrimaryContainer` ink. Settings picks a [layout] (a row
 * of three, or a bento whose square goes to [lastUsed]) and [stones] (the pieces of one split stone,
 * or plain rounded tiles).
 *
 * Stateless: a tap plays the selection haptic (behind [hapticsOn]) and hands the shortcut to
 * [onShortcut], whose owner stores it as the last used and opens its flow. [cameraEnabled] false
 * dims the camera piece to 0.6 and disables it, while a camera moment saves. Above 1.5 × font scale
 * the row stacks its three pieces vertically; the bento instead grows past square.
 */
@Composable
fun HomeShortcuts(
    layout: HomeShortcutsLayout,
    stones: Boolean,
    lastUsed: HomeShortcut,
    onShortcut: (HomeShortcut) -> Unit,
    modifier: Modifier = Modifier,
    cameraEnabled: Boolean = true,
    hapticsOn: () -> Boolean = { true },
) {
    val haptic = rememberTapHaptics(hapticsOn)
    val colors = MaterialTheme.colorScheme
    val look =
        ShortcutLook(
            fill = colors.primaryContainer,
            ink = colors.onPrimaryContainer,
            cameraEnabled = cameraEnabled,
            onTap = { shortcut ->
                haptic(TapHaptic.SELECTION)
                onShortcut(shortcut)
            },
        )
    val stacked = LocalDensity.current.fontScale > STACK_ABOVE_FONT_SCALE
    when (layout) {
        HomeShortcutsLayout.ROW ->
            if (stones) {
                StoneRow(look, stacked, modifier)
            } else {
                PlainRow(look, stacked, modifier)
            }
        HomeShortcutsLayout.BENTO -> Bento(HomeShortcut.bento(lastUsed), stones, look, modifier)
    }
}

/** What every cell needs, whatever the style. */
private class ShortcutLook(
    val fill: Color,
    val ink: Color,
    val cameraEnabled: Boolean,
    val onTap: (HomeShortcut) -> Unit,
) {
    fun enabled(shortcut: HomeShortcut): Boolean = shortcut != HomeShortcut.CAMERA || cameraEnabled

    fun fillFor(shortcut: HomeShortcut): Color = if (enabled(shortcut)) fill else fill.copy(alpha = DIMMED)

    fun contentAlpha(shortcut: HomeShortcut): Float = if (enabled(shortcut)) 1f else DIMMED
}

/** A cell's button: its label is its name (merged from the text), its ripple already clipped by the caller. */
private fun Modifier.shortcutButton(
    shortcut: HomeShortcut,
    look: ShortcutLook,
): Modifier = clickable(enabled = look.enabled(shortcut), role = Role.Button) { look.onTap(shortcut) }

// MARK: - Row

@Composable
private fun StoneRow(
    look: ShortcutLook,
    stacked: Boolean,
    modifier: Modifier,
) {
    val shortcuts = HomeShortcut.entries
    SplitStone(
        n = shortcuts.size,
        seed = StoneGeometry.QUICK_ACTIONS_SEED,
        fills = shortcuts.map(look::fillFor),
        modifier = modifier.fillMaxWidth(),
        minHeight = if (stacked) 0.dp else ROW_MIN_HEIGHT,
        vertical = stacked,
    ) { index ->
        val shortcut = shortcuts[index]
        Box(Modifier.pieceClip(index).shortcutButton(shortcut, look), propagateMinConstraints = true) {
            val content = Modifier.alpha(look.contentAlpha(shortcut))
            if (stacked) {
                StackedCell(shortcut, look.ink, content)
            } else {
                RowCell(shortcut, first = index == 0, ink = look.ink, modifier = content)
            }
        }
    }
}

@Composable
private fun PlainRow(
    look: ShortcutLook,
    stacked: Boolean,
    modifier: Modifier,
) {
    val shortcuts = HomeShortcut.entries
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GUTTER)) {
            shortcuts.forEach { shortcut ->
                PlainTile(shortcut, look, Modifier.fillMaxWidth()) { StackedCell(shortcut, look.ink, it) }
            }
        }
    } else {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GUTTER)) {
            shortcuts.forEachIndexed { index, shortcut ->
                PlainTile(shortcut, look, Modifier.weight(1f).fillMaxHeight().heightIn(min = ROW_MIN_HEIGHT)) {
                    RowCell(shortcut, first = index == 0, ink = look.ink, modifier = it)
                }
            }
        }
    }
}

/** A rounded tile ([PageShapes.card]'s 18 dp) in place of a piece. */
@Composable
private fun PlainTile(
    shortcut: HomeShortcut,
    look: ShortcutLook,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier
            .clip(PageShapes.card)
            .background(look.fillFor(shortcut))
            .shortcutButton(shortcut, look),
        propagateMinConstraints = true,
    ) {
        content(Modifier.alpha(look.contentAlpha(shortcut)))
    }
}

/** The row's cell (the mock's `.act`): the icon at the top, the label at the bottom, both at the start. */
@Composable
private fun RowCell(
    shortcut: HomeShortcut,
    first: Boolean,
    ink: Color,
    modifier: Modifier,
) {
    Column(
        modifier.padding(start = if (first) 16.dp else 20.dp, top = 15.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PbblsIconImage(shortcut.icon, PbblsIconSize.TILE, contentDescription = null, tint = ink)
        Spacer(Modifier.height(6.dp))
        Label(shortcut, ink, TextAlign.Start)
    }
}

/** A stacked piece, at large font scales: the icon, then the label beside it. */
@Composable
private fun StackedCell(
    shortcut: HomeShortcut,
    ink: Color,
    modifier: Modifier,
) {
    Row(
        modifier.padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PbblsIconImage(shortcut.icon, PbblsIconSize.TILE, contentDescription = null, tint = ink)
        Spacer(Modifier.width(12.dp))
        Label(shortcut, ink, TextAlign.Start)
    }
}

@Composable
private fun Label(
    shortcut: HomeShortcut,
    ink: Color,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(shortcut.labelRes),
        style = PebblesTheme.page.actionLabel,
        color = ink,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// MARK: - Bento

/**
 * The bento (iOS `ShortcutsBentoLayout`): [order]'s first a square at 45 % of the width, the other
 * two stacked beside it, together the square's height. It grows past square only when the lead's
 * text, or twice a stacked cell's, needs more. The cells are [StoneGeometry.bentoCells], and with
 * [stones] each draws its piece of [StoneGeometry.splitBento], one calm stone clipped along the
 * gutters. The square stays on the left in either direction: its piece is drawn for that side.
 */
@Composable
private fun Bento(
    order: List<HomeShortcut>,
    stones: Boolean,
    look: ShortcutLook,
    modifier: Modifier,
) {
    val stone = remember(order) { BentoStone(order.map { it.raw }) }
    Layout(
        content = {
            order.forEachIndexed { index, shortcut ->
                val shape = if (stones) stone.shapes[index] else PageShapes.card
                Box(
                    Modifier
                        .background(look.fillFor(shortcut), shape)
                        .clip(shape)
                        .shortcutButton(shortcut, look),
                    propagateMinConstraints = true,
                ) {
                    val content = Modifier.alpha(look.contentAlpha(shortcut))
                    if (index == 0) LeadCell(shortcut, look.ink, content) else PairCell(shortcut, look.ink, content)
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val widthDp = width / density
        val leadDp = (widthDp * LEAD_SHARE).roundToInt().toFloat()
        val leadPx = (leadDp * density).roundToInt()
        val gutterPx = GUTTER.roundToPx()
        val pairPx = (width - leadPx - gutterPx).coerceAtLeast(0)
        val leadNeeds = measurables[0].maxIntrinsicHeight(leadPx)
        val pairNeeds = max(measurables[1].maxIntrinsicHeight(pairPx), measurables[2].maxIntrinsicHeight(pairPx))
        val height = maxOf(leadPx, leadNeeds, 2 * pairNeeds + gutterPx).coerceIn(constraints.minHeight, constraints.maxHeight)
        val size = Size(widthDp, height / density)
        stone.size = size
        stone.leadWidth = leadDp
        val placed =
            StoneGeometry.bentoCells(size, leadDp).zip(measurables) { cell, measurable ->
                val left = (cell.left * density).roundToInt()
                val top = (cell.top * density).roundToInt()
                val right = (cell.right * density).roundToInt()
                val bottom = (cell.bottom * density).roundToInt()
                Triple(left, top, measurable.measure(Constraints.fixed((right - left).coerceAtLeast(0), (bottom - top).coerceAtLeast(0))))
            }
        layout(width, height) {
            placed.forEach { (x, y, placeable) -> placeable.place(x, y) }
        }
    }
}

/** The square: a larger icon over the label, centred. */
@Composable
private fun LeadCell(
    shortcut: HomeShortcut,
    ink: Color,
    modifier: Modifier,
) {
    Column(
        modifier.padding(14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PbblsIconImage(shortcut.icon, contentDescription = null, size = LEAD_ICON, tint = ink)
        Spacer(Modifier.height(9.dp))
        Label(shortcut, ink, TextAlign.Center)
    }
}

/** A stacked cell beside the square: icon then label, on one line at the start. */
@Composable
private fun PairCell(
    shortcut: HomeShortcut,
    ink: Color,
    modifier: Modifier,
) {
    Row(
        modifier.padding(start = 18.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PbblsIconImage(shortcut.icon, PbblsIconSize.TILE, contentDescription = null, tint = ink)
        Spacer(Modifier.width(10.dp))
        Label(shortcut, ink, TextAlign.Start)
    }
}

/**
 * The bento's size and square width in dp, written by the layout and read by the pieces' shapes
 * when they draw (as `SplitStone`'s state): state, so a new size redraws the pieces.
 */
@Stable
private class BentoStone(
    private val order: List<String>,
) {
    var size by mutableStateOf(Size.Zero)
    var leadWidth by mutableFloatStateOf(0f)

    val shapes: List<Shape> = List(order.size) { PieceShape(it) }

    private inner class PieceShape(
        private val index: Int,
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val whole = this@BentoStone.size
            val pieces = StoneGeometry.splitBento(whole, leadWidth, order)
            val cells = StoneGeometry.bentoCells(whole, leadWidth)
            return Outline.Generic(StoneGeometry.path(pieces[index], density.density, cells[index].topLeft))
        }
    }
}
