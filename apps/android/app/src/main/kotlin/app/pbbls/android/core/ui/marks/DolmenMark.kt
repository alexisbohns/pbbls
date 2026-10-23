package app.pbbls.android.core.ui.marks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline

private val UPRIGHT_LENGTH = 20.dp
private val UPRIGHT_THICKNESS = 11.dp
private val UPRIGHT_SPREAD = 13.dp
private val CAP_WIDTH = 29.dp
private val CAP_HEIGHT = 10.dp
private val CAP_SINK = 3.dp

/**
 * A month as a dolmen (#1207, D1): two standing stones under a capstone, in the cairn's box and
 * stones, in one [color]. Decorative: the strip item says the month in words.
 */
@Composable
fun DolmenMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val outline = rememberStoneOutline(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL)
    val box = StoneGeometry.outlineBox(ValenceSizeGroup.MEDIUM)
    Canvas(modifier.size(MARK_WIDTH, MARK_HEIGHT)) {
        val length = UPRIGHT_LENGTH.toPx()
        val thickness = UPRIGHT_THICKNESS.toPx()
        val uprightTop = size.height - length
        // An upright is a stone stood on end: drawn lying, then turned a quarter about its centre.
        listOf(-1, 1).forEach { side ->
            val cx = size.width / 2 + side * UPRIGHT_SPREAD.toPx() / 2
            val cy = uprightTop + length / 2
            rotate(90f, Offset(cx, cy)) {
                translate(cx - size.width / 2, cy - thickness / 2) {
                    drawFlatStone(outline.path, box.width, box.height, length, thickness, 0f, color, stroke = null)
                }
            }
        }
        val capHeight = CAP_HEIGHT.toPx()
        drawFlatStone(
            outline.path,
            box.width,
            box.height,
            CAP_WIDTH.toPx(),
            capHeight,
            uprightTop - capHeight + CAP_SINK.toPx(),
            color,
            stroke = null,
        )
    }
}
