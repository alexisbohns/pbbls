package app.pbbls.android.features.home.checklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The mock's flat pebble, in a 24-unit box (c3 `BLOB`). */
private const val BLOB =
    "M11.8 4.6 C17.2 4.5 21 7.4 20.9 11.6 C20.8 16.2 17 19.2 11.7 19.3 C7 19.3 3.3 16.4 3.3 12 C3.3 7.5 6.8 4.7 11.8 4.6 Z"

/** The tick inside a done mark, in the same box. */
private const val CHECK = "M8 12.2 L10.7 14.8 L16 9.3"

private const val BOX = 24f
private const val CHECK_WIDTH = 2f
private val OUTLINE_WIDTH = 1.5.dp

private val blobPath: Path = PathParser().parsePathString(BLOB).toPath()
private val checkPath: Path = PathParser().parsePathString(CHECK).toPath()

/**
 * A first-five task's mark (#1383 D11): a small flat pebble, open (an `outline` rim, 1.5 dp) while the
 * task is to do, filled `primary` with an `onPrimary` tick once done. Decorative: the row's state says it.
 */
@Composable
internal fun TaskMark(
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / BOX
        scale(k, pivot = Offset.Zero) {
            if (done) {
                drawPath(blobPath, colors.primary)
                drawPath(
                    checkPath,
                    colors.onPrimary,
                    style = Stroke(width = CHECK_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawPath(blobPath, colors.outline, style = Stroke(width = OUTLINE_WIDTH.toPx() / k))
            }
        }
    }
}
