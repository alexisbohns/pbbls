package app.pbbls.android.features.home.wraps

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

private val DASH_WIDTH = 1.dp
private val DASH = 4.dp

/**
 * A 1 dp outline in 4 dp dashes around [shape] (iOS `StrokeStyle(lineWidth: 1, dash: [4, 4])`): the promise
 * card and the folded sheet's placeholders, things that are not there yet.
 */
internal fun Modifier.dashedOutline(
    color: Color,
    shape: Shape,
): Modifier =
    drawBehind {
        val width = DASH_WIDTH.toPx()
        val dash = DASH.toPx()
        // Inset by half the stroke so the whole line is inside the box, as iOS's strokeBorder.
        val inset = width / 2
        val outline = shape.createOutline(size.copy(width = size.width - width, height = size.height - width), layoutDirection, this)
        translate(inset, inset) {
            drawOutline(outline, color, style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
        }
    }
