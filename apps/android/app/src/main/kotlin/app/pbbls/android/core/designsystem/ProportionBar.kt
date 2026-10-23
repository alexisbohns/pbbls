package app.pbbls.android.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One share of a [ProportionBar]: a non-negative weight and its colour. */
data class BarSegment(
    val weight: Float,
    val color: Color,
)

/**
 * Shares as one bar (#1207): the emotions tile, a domain row. Segments keep their order, take the
 * width in proportion to their weights, are rounded at their ends and separated by a 2 dp gap.
 * Zero-weight segments take no room. Decorative: the caller says the shares in words.
 */
@Composable
fun ProportionBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val shown = segments.filter { it.weight > 0f }
        val total = shown.sumOf { it.weight.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val gap = SpineLayoutTokens.BAR_GAP.toPx()
        val room = size.width - gap * (shown.size - 1)
        val corner = CornerRadius(size.height / 2)
        var x = 0f
        shown.forEach { segment ->
            val w = room * segment.weight / total
            drawRoundRect(segment.color, Offset(x, 0f), Size(w, size.height), corner)
            x += w + gap
        }
    }
}
