package app.pbbls.android.core.ui.marks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline

/** The mark's box: as wide as the widest stone with room, as tall as the cap. */
internal val MARK_WIDTH = 30.dp
internal val MARK_HEIGHT = 42.dp

private val NEXT_STROKE = 1.dp
private val NEXT_DASH = 2.dp

/** A settled week, or the week being lived, whose next stone is drawn dotted. */
enum class CairnState { SETTLED, LIVE }

/**
 * A week as a cairn (#1207, D1): one flat stone per day that holds a moment, widest at the base,
 * stacked from the bottom of a 30 × 42 dp box, in one [color] (the caller inks the focused week).
 * Decorative: the strip item says the week in words.
 */
@Composable
fun CairnMark(
    days: List<DayWeight>,
    state: CairnState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val outline = rememberStoneOutline(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL)
    val box = StoneGeometry.outlineBox(ValenceSizeGroup.MEDIUM)
    val aspect = box.width / box.height
    val widths = remember(days) { CairnGeometry.widths(days) }
    val live = state == CairnState.LIVE
    Canvas(modifier.size(MARK_WIDTH, MARK_HEIGHT)) {
        // Widths and heights are dp from CairnGeometry; a busy week flattens its stones rather than narrowing them.
        val squash = CairnGeometry.squash(widths, aspect, live)
        var bottom = size.height
        widths.forEachIndexed { index, w ->
            val h = CairnGeometry.height(w, aspect) * squash * density
            if (index > 0) bottom += h * CairnGeometry.OVERLAP
            drawFlatStone(outline.path, box.width, box.height, w * density, h, bottom - h, color, stroke = null)
            bottom -= h
        }
        if (live) {
            val h = CairnGeometry.height(CairnGeometry.NEXT_WIDTH, aspect) * squash * density
            if (widths.isNotEmpty()) bottom += h * CairnGeometry.OVERLAP
            drawFlatStone(
                outline.path,
                box.width,
                box.height,
                CairnGeometry.NEXT_WIDTH * density,
                h,
                bottom - h,
                color,
                stroke = NEXT_STROKE.toPx(),
            )
        }
    }
}

/** The outline (a [boxWidth] × [boxHeight] unit box) drawn [width] × [height] px, centred, its top at [top]; filled, or dotted at [stroke] px. */
internal fun DrawScope.drawFlatStone(
    outline: Path,
    boxWidth: Float,
    boxHeight: Float,
    width: Float,
    height: Float,
    top: Float,
    color: Color,
    stroke: Float?,
) {
    val sx = width / boxWidth
    val sy = height / boxHeight
    withTransform({
        translate((size.width - width) / 2, top)
        scale(sx, sy, Offset.Zero)
    }) {
        if (stroke == null) {
            drawPath(outline, color)
        } else {
            val dash = NEXT_DASH.toPx() / sx
            drawPath(outline, color, style = Stroke(stroke / sx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
        }
    }
}
