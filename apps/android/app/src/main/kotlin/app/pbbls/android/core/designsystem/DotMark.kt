package app.pbbls.android.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * How a day dot reads (#1207). [FILLED] a day present; [RING] a day of the span without it (the
 * web's inset ring at `opacity-70`); [SOCKET] an empty slot on a ring that holds stones (the
 * garland), fainter so the stones carry it; [QUIET] a filled dot pushed back, for a day outside
 * the span (a calendar's padding).
 */
enum class DotStyle { FILLED, RING, SOCKET, QUIET }

private const val RING_ALPHA = 0.7f
private const val SOCKET_ALPHA = 0.4f
private const val QUIET_ALPHA = 0.25f

/** The single primitive behind every dot: [diameter] and [ringWidth] in px, centred on [center]. */
fun DrawScope.dotMark(
    center: Offset,
    diameter: Float,
    style: DotStyle,
    color: Color,
    ringWidth: Float,
) {
    val radius = diameter / 2
    when (style) {
        DotStyle.FILLED -> drawCircle(color, radius, center)
        DotStyle.RING -> drawCircle(color, radius - ringWidth / 2, center, alpha = RING_ALPHA, style = Stroke(ringWidth))
        DotStyle.SOCKET -> drawCircle(color, radius - ringWidth / 2, center, alpha = SOCKET_ALPHA, style = Stroke(ringWidth))
        DotStyle.QUIET -> drawCircle(color, radius, center, alpha = QUIET_ALPHA)
    }
}

/** One dot as a composable, for a key or a legend. Decorative. */
@Composable
fun DotMark(
    style: DotStyle,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = SpineLayoutTokens.DOT,
) {
    Canvas(modifier.size(diameter)) {
        dotMark(center, size.minDimension, style, color, SpineLayoutTokens.DOT_RING.toPx())
    }
}
