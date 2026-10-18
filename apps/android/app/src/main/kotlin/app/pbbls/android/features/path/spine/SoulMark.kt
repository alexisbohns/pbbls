package app.pbbls.android.features.path.spine

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.render.GlyphImage

// The placeholder of a soul without a glyph: the web's `SoulMark`, a dashed 1 px square with 3 px corners.
private val PLACEHOLDER_STROKE = 1.dp
private val PLACEHOLDER_RADIUS = 3.dp
private val PLACEHOLDER_DASH = 3.dp
private val PLACEHOLDER_GAP = 2.dp

/** A soul's glyph as a thin line, or, without one, the dashed placeholder square (web `SoulMark`). */
@Composable
internal fun SoulMark(
    glyph: Glyph?,
    ink: Color,
    modifier: Modifier,
) {
    if (glyph != null) {
        GlyphImage(strokes = glyph.strokes, viewBox = glyph.viewBox, strokeColor = ink, modifier = modifier)
    } else {
        val placeholder = PebblesTheme.spine.colors.ink3
        Canvas(modifier) {
            val stroke = PLACEHOLDER_STROKE.toPx()
            val inset = stroke / 2
            drawRoundRect(
                color = placeholder,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(PLACEHOLDER_RADIUS.toPx()),
                style =
                    Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(PLACEHOLDER_DASH.toPx(), PLACEHOLDER_GAP.toPx())),
                    ),
            )
        }
    }
}
