package app.pbbls.android.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A drawn checkmark glyph, sized [size] square. No icon-library dependency. */
@Composable
internal fun CheckGlyph(
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val strokeWidth = this.size.minDimension * 0.14f
        val path =
            Path().apply {
                moveTo(width * 0.18f, height * 0.55f)
                lineTo(width * 0.42f, height * 0.78f)
                lineTo(width * 0.84f, height * 0.24f)
            }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
