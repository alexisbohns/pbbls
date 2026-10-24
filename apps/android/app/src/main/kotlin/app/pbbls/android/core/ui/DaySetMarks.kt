package app.pbbls.android.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.DotStyle
import app.pbbls.android.core.designsystem.RadialLayout
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DAY_CIRCLE
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DAY_CIRCLE_DOT
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DAY_CIRCLE_GAP_SLOTS
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT_GAP
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT_RING
import app.pbbls.android.core.designsystem.dotMark
import app.pbbls.android.core.model.wrap.DaySet

private const val WEEK = 7
private val MIN_RING_DOT = 3.dp
private const val RING_LINE_ALPHA = 0.7f

/** How a [DaySet] is laid out. */
sealed interface DayMode {
    /** One row, the span's days in order: a week's seven. */
    data object Row : DayMode

    /** Weeks of seven, the first day in the column [leading] (0 = the week's first day), padding days quiet. */
    data class Calendar(
        val leading: Int,
    ) : DayMode

    /** The span around a ring from the top, clockwise, a gap where it closes. */
    data object Circle : DayMode
}

/**
 * The days a soul, a family or a domain touches (#1207): filled on a day present, an inset ring
 * otherwise. One semantics node: [contentDescription] says the days in words; without it the marks
 * are decorative (a row that speaks for itself).
 */
@Composable
fun DaySetMarks(
    days: DaySet,
    mode: DayMode,
    ink: Color,
    ring: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    ringSize: Dp = DAY_CIRCLE,
) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val box = DaySetGeometry.size(days.length, mode, ringSize)
    Canvas(modifier.size(box.width, box.height).then(described)) {
        val ringWidth = DOT_RING.toPx()
        when (mode) {
            DayMode.Row, is DayMode.Calendar -> {
                val dot = DOT.toPx()
                val step = dot + DOT_GAP.toPx()
                val leading = (mode as? DayMode.Calendar)?.leading ?: 0
                val cells = if (mode is DayMode.Calendar) DaySetGeometry.rows(days.length, leading) * WEEK else days.length
                repeat(cells) { cell ->
                    val day = cell - leading
                    val column = if (mode is DayMode.Calendar) cell % WEEK else cell
                    val row = if (mode is DayMode.Calendar) cell / WEEK else 0
                    val style =
                        when {
                            day !in 0 until days.length -> DotStyle.QUIET
                            day in days -> DotStyle.FILLED
                            else -> DotStyle.RING
                        }
                    // A calendar's padding is the empty quiet: a ring's colour, not the ink.
                    val color = if (style == DotStyle.FILLED) ink else ring
                    dotMark(Offset(column * step + dot / 2, row * step + dot / 2), dot, style, color, ringWidth)
                }
            }
            DayMode.Circle -> {
                // The board's ring: one thin line round the month, open across the 1st with a tick
                // there, and a dot on each day present only, so the eye reads the days, not the grid.
                val dot = (DAY_CIRCLE_DOT * (ringSize / DAY_CIRCLE)).coerceAtLeast(MIN_RING_DOT).toPx()
                val ringLayout = RadialLayout(days.length, gapSlots = DAY_CIRCLE_GAP_SLOTS)
                val radius = size.minDimension / 2 - dot / 2
                val start = ringLayout.angleDeg(-0.5f)
                drawArc(
                    color = ring,
                    startAngle = start,
                    sweepAngle = ringLayout.angleDeg(days.length - 0.5f) - start,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    alpha = RING_LINE_ALPHA,
                    style = Stroke(ringWidth),
                )
                drawLine(ring, Offset(center.x, center.y - radius - dot / 2), Offset(center.x, center.y - radius + dot / 2), ringWidth)
                days.days.forEach { day -> dotMark(ringLayout.point(day, center, radius), dot, DotStyle.FILLED, ink, ringWidth) }
            }
        }
    }
}

/** The marks' box, in dp: a row is as wide as its dots, a calendar as tall as its weeks, a ring square. */
internal object DaySetGeometry {
    data class Box(
        val width: Dp,
        val height: Dp,
    )

    fun rows(
        length: Int,
        leading: Int,
    ): Int = (leading + length + WEEK - 1) / WEEK

    fun size(
        length: Int,
        mode: DayMode,
        ringSize: Dp = DAY_CIRCLE,
    ): Box =
        when (mode) {
            DayMode.Row -> Box(DOT * length + DOT_GAP * (length - 1).coerceAtLeast(0), DOT)
            is DayMode.Calendar -> {
                val rows = rows(length, mode.leading)
                Box(DOT * WEEK + DOT_GAP * (WEEK - 1), DOT * rows + DOT_GAP * (rows - 1).coerceAtLeast(0))
            }
            DayMode.Circle -> Box(ringSize, ringSize)
        }
}
