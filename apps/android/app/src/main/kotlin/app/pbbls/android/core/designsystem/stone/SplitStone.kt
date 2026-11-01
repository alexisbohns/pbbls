package app.pbbls.android.core.designsystem.stone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** What a [SplitStone] cell can ask of its stone. */
@Stable
interface SplitStoneScope {
    /**
     * Clips to piece [index]'s outline, in the cell's own coordinates. Put it
     * before a `clickable` so the ripple takes the piece's shape.
     */
    fun Modifier.pieceClip(index: Int): Modifier
}

/**
 * One rock cut into [n] pieces, a cell on each (spec D5): the quick actions,
 * the counters, the first-five stepper. The row is as wide as its constraints
 * allow; each cell gets its [StoneGeometry.rowCells] slot and piece *k* is
 * drawn behind cell *k* in `fills[k]`. Heights are minimums: the row is as
 * tall as its tallest cell's content, never below [minHeight], so text grows
 * the stone at large font scales (D18). Cells are measured by their maximum
 * intrinsic height, so cell content must not subcompose (no `LazyColumn`,
 * no `BoxWithConstraints`).
 *
 * [vertical] stacks the cells as [n] full-width stones (seeds `<seed>#k`),
 * [StoneOptions.gutter] apart, for the large-font case where three pieces side
 * by side no longer fit their text (D18); the caller decides when.
 *
 * Right to left, the first cell sits on the right and takes the piece drawn
 * there: the rock itself is never mirrored.
 */
@Composable
fun SplitStone(
    n: Int,
    seed: String,
    fills: List<Color>,
    modifier: Modifier = Modifier,
    options: StoneOptions = StoneOptions(),
    minHeight: Dp = 0.dp,
    vertical: Boolean = false,
    content: @Composable SplitStoneScope.(index: Int) -> Unit,
) {
    require(fills.size >= n) { "SplitStone needs one fill per piece: $n pieces, ${fills.size} fills" }
    val stone = remember(n, seed, options, vertical) { SplitStoneState(n, seed, options, vertical) }
    Layout(
        content = {
            for (k in 0 until n) {
                Box(Modifier.background(fills[k], stone.shape(k)), propagateMinConstraints = true) {
                    stone.content(k)
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val minHeightPx = minHeight.roundToPx()
        if (vertical) {
            val gutterPx = options.gutter.dp.roundToPx()
            val heights = measurables.map { max(minHeightPx, it.maxIntrinsicHeight(width)) }
            val placeables = measurables.mapIndexed { k, cell -> cell.measure(Constraints.fixed(width, heights[k])) }
            val height = (heights.sum() + gutterPx * (n - 1).coerceAtLeast(0)).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.place(0, y)
                    y += it.height + gutterPx
                }
            }
        } else {
            val widthDp = width / density
            val cellWidth = (((widthDp - (n - 1) * options.gutter) / n) * density).roundToInt().coerceAtLeast(0)
            val contentHeight = measurables.maxOfOrNull { it.maxIntrinsicHeight(cellWidth) } ?: 0
            val height = max(max(minHeightPx, contentHeight), constraints.minHeight).coerceAtMost(constraints.maxHeight)
            val rowSize = Size(widthDp, height / density)
            stone.rowSize = rowSize
            val cells = StoneGeometry.rowCells(rowSize, n, options.gutter)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val placed =
                measurables.mapIndexed { k, cell ->
                    val slot = cells[if (rtl) n - 1 - k else k]
                    val left = (slot.left * density).roundToInt()
                    val right = (slot.right * density).roundToInt()
                    left to cell.measure(Constraints.fixed((right - left).coerceAtLeast(0), height))
                }
            layout(width, height) {
                placed.forEach { (x, placeable) -> placeable.place(x, 0) }
            }
        }
    }
}

/**
 * The row's size in dp, written by the layout and read by the pieces' shapes
 * when they draw. It is state, so a new size redraws the pieces even when a
 * cell's rounded pixel size did not change.
 */
@Stable
private class SplitStoneState(
    val n: Int,
    val seed: String,
    val options: StoneOptions,
    val vertical: Boolean,
) : SplitStoneScope {
    var rowSize by mutableStateOf(Size.Zero)

    private val shapes =
        List(n) { k ->
            if (vertical) StoneShape("$seed#$k", options.corner.dp, options.bow) else PieceShape(this, k)
        }

    fun shape(index: Int): Shape = shapes[index]

    override fun Modifier.pieceClip(index: Int): Modifier = clip(shapes[index])
}

/** Piece [index] of the row, drawn in its cell's coordinates. */
private class PieceShape(
    private val stone: SplitStoneState,
    private val index: Int,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rowSize = stone.rowSize
        val slot = if (layoutDirection == LayoutDirection.Rtl) stone.n - 1 - index else index
        val pieces = StoneGeometry.splitRow(rowSize, stone.n, stone.seed, stone.options)
        val cells = StoneGeometry.rowCells(rowSize, stone.n, stone.options.gutter)
        return Outline.Generic(StoneGeometry.path(pieces[slot], density.density, cells[slot].topLeft))
    }
}
