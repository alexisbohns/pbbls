package app.pbbls.android.features.home.wraps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The mock's ring (`type/five/c3.html`, `ring(w, 116, 1.9, 3.6)`), in dp. */
private val RING = 116.dp
private val FILLED_R = 3.6.dp
private val EMPTY_R = 1.9.dp
private val EMPTY_STROKE = 1.1.dp

/** The ring's centre line sits a filled dot's radius and 1 dp inside its box, so no dot is cut. */
private val RING_INSET = 1.dp

/**
 * A revealed wrap's ring (#1383 D12): one dot per day of the month on a 116 dp circle, the first day at the
 * top and the rest clockwise. A day in [pebbled] (0-based, as the cover's days) is a filled `primary` dot, any
 * other an `outlineVariant` ring; [count] sits in the middle in `wrapFigure`. Decorative: the card says the
 * month and its count in one label.
 */
@Composable
internal fun WrapRing(
    daysInMonth: Int,
    pebbled: Set<Int>,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.size(RING), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val fillR = FILLED_R.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - fillR - RING_INSET.toPx()
            val ring = Stroke(EMPTY_STROKE.toPx())
            val emptyR = EMPTY_R.toPx()
            for (day in 0 until daysInMonth) {
                val angle = -PI / 2 + day * 2 * PI / daysInMonth
                val at = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
                if (day in pebbled) {
                    drawCircle(filled, radius = fillR, center = at)
                } else {
                    drawCircle(empty, radius = emptyR, center = at, style = ring)
                }
            }
        }
        Text(
            text = count.toString(),
            style = PebblesTheme.page.wrapFigure,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
    }
}
