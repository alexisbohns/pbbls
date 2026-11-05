package app.pbbls.android.core.designsystem.stone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A rock cut into `rows × columns` pieces (#1510 D7): one [SplitStone] per row, seeded `<seed>#row`,
 * stacked [StoneOptions.gutter] apart, so the pieces read as one stone cut both ways. Cell `k` is
 * row-major ([cellIndex]); `fills[k]` is its colour; the content draws into it with its piece's clip.
 */
object StoneGrid {
    fun rowSeeds(
        seed: String,
        rows: Int,
    ): List<String> = List(rows) { "$seed#$it" }

    fun cellIndex(
        row: Int,
        column: Int,
        columns: Int,
    ): Int = row * columns + column
}

/**
 * The [StoneGrid] laid out: [rows] split stones of [columns] pieces each. [content] receives the
 * row-major cell index and runs in that row's [SplitStoneScope], so `pieceClip(index % columns)`
 * is the piece under it. Cells are measured by intrinsic height (see [SplitStone]): no subcomposition.
 */
@Composable
fun StoneGridLayout(
    rows: Int,
    columns: Int,
    seed: String,
    fills: List<Color>,
    modifier: Modifier = Modifier,
    options: StoneOptions = StoneOptions(),
    minCellHeight: Dp = 88.dp,
    content: @Composable SplitStoneScope.(index: Int, column: Int) -> Unit,
) {
    require(fills.size >= rows * columns) { "StoneGrid needs one fill per cell: ${rows * columns} cells, ${fills.size} fills" }
    val seeds = StoneGrid.rowSeeds(seed, rows)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(options.gutter.dp)) {
        for (row in 0 until rows) {
            SplitStone(
                n = columns,
                seed = seeds[row],
                fills = fills.subList(row * columns, (row + 1) * columns),
                options = options,
                minHeight = minCellHeight,
            ) { column -> content(StoneGrid.cellIndex(row, column, columns), column) }
        }
    }
}
