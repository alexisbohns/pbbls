package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.MonthState
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.figures.YearDiscGeometry as G

private val STONE_PATH: Path by lazy { PathParser().parsePathString(G.STONE).toPath() }
private const val STONE_EDGE = 1.2f
private const val STONE_EDGE_ALPHA = 0.85f
private const val RING_ALPHA = 0.5f
private const val RING_2_ALPHA = 0.45f
private const val SPOKE = 0.7f
private const val SPOKE_MUTED = 0.4f
private const val SPOKE_RIM = 1f
private const val NOTCH = 1f
private const val NOTCH_QUIET = 0.6f
private const val GOAL = 1.3f
private const val CLIMB = 1.8f
private const val RIM_RING = 0.78f
private const val RIM_RING_WIDTH = 0.8f
private const val CROSS = 1.1f
private const val CROSS_HALF = 4f

// The board's counters are rounded neutral stones, solid in a month's hue.
private val COUNTER_SOLID = InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, ValencePolarity.NEUTRAL)
private val COUNTER_BLANK = InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.BLANK)
private val COUNTER_DOTTED = InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.DOTTED)

/**
 * Each month's hue, in month order: a month the server calls kept takes its family's, or ink for a
 * family Android does not know (spec §4); any other month has none.
 */
@Composable
internal fun monthHues(
    months: List<AchievementMonth>,
    lookups: AchievementLookups,
): List<Color?> {
    val colors = PebblesTheme.spine.colors
    return months.map { m ->
        if (m.state != MonthState.KEPT) null else m.family?.let { lookups.family(it)?.palette?.hue(colors.isNight) } ?: colors.ink
    }
}

/** The counter prints a year needs: a solid seal per hue, and the blank and dotted ones in secondary ink. */
@Composable
internal fun rememberCounterPrints(hues: List<Color?>): InkPrints {
    val colors = PebblesTheme.spine.colors
    val keys =
        hues.filterNotNull().map { InkKey(COUNTER_SOLID, it) } +
            listOf(
                InkKey(COUNTER_BLANK, colors.ink2),
                InkKey(COUNTER_DOTTED, colors.ink2),
            )
    return rememberInkPrints(keys, colors.stock, SealVariant.MINI)
}

/** A month's counter at [center], [width] px wide (the board's `monthCounter`). */
internal fun DrawScope.monthCounter(
    mark: MonthMark,
    hue: Color?,
    center: Offset,
    width: Float,
    prints: InkPrints,
    colors: SpineColors,
    unit: Float,
) {
    if (hue != null) {
        if (mark.rim) drawCircle(colors.ink, width * RIM_RING, center, style = Stroke(RIM_RING_WIDTH * unit))
        sealAt(prints.of(COUNTER_SOLID, hue), center, width)
    } else {
        sealAt(prints.of(if (mark.upcoming) COUNTER_DOTTED else COUNTER_BLANK, colors.ink2), center, width)
    }
}

/**
 * The docked year disc (D12; the board's B, decided 28 October): twelve spokes on a stone, each
 * month's counter docked where its level reached, its progress inked along the spoke. One node:
 * [description], the disc read as the server's lists.
 */
@Composable
fun YearDisc(
    year: AchievementYear,
    lookups: AchievementLookups,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hues = monthHues(year.months, lookups)
    val prints = rememberCounterPrints(hues)
    val marks = remember(year) { year.months.map(G::mark) }
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .widthIn(max = size)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val unit = this.size.width / G.VIEW

        fun px(p: Offset) = (p - Offset(G.VIEW_MIN, G.VIEW_MIN)) * unit

        withTransform({
            scale(unit, unit, Offset.Zero)
            translate(-G.VIEW_MIN, -G.VIEW_MIN)
        }) {
            drawPath(STONE_PATH, colors.stock)
            drawPath(STONE_PATH, colors.ink2, alpha = STONE_EDGE_ALPHA, style = Stroke(STONE_EDGE, join = StrokeJoin.Round))
            G.GRAIN.forEach { (x, y, r) -> drawCircle(colors.ink3, r, Offset(x, y)) }
            drawCircle(colors.ink2, G.INNER_RING, Offset(G.CENTRE, G.CENTRE), alpha = RING_ALPHA, style = Stroke(1f))
            drawCircle(colors.ink2, G.INNER_RING_2, Offset(G.CENTRE, G.CENTRE), alpha = RING_2_ALPHA, style = Stroke(0.5f))
            marks.forEachIndexed { i, mark ->
                val hue = hues[i]
                val spokeColour = if (mark.rim && hue != null) hue else colors.ink2
                val spokeWidth =
                    if (mark.rim && hue != null) {
                        SPOKE_RIM
                    } else if (mark.muted) {
                        SPOKE_MUTED
                    } else {
                        SPOKE
                    }
                drawLine(spokeColour, G.point(G.SPOKE_IN, mark.angle), G.point(G.SPOKE_OUT, mark.angle), spokeWidth)
                listOf(G.RADII[1] to false, G.RADII[2] to true).forEach { (r, isRim) ->
                    val (a, b) = G.notch(mark.month, r)
                    val quiet = mark.muted || (isRim && !mark.rim)
                    drawLine(colors.ink2, a, b, if (quiet) NOTCH_QUIET else NOTCH)
                }
                if (mark.goalNotch) {
                    drawLine(
                        colors.ink,
                        G.point(G.RADII[1], mark.angle + G.GOAL_NOTCH_DEG),
                        G.point(G.RADII[1], mark.angle - G.GOAL_NOTCH_DEG),
                        GOAL,
                        StrokeCap.Round,
                    )
                }
                mark.climb?.let { climb ->
                    val colour = if (mark.climbInHue && hue != null) hue else colors.ink
                    drawLine(colour, G.point(climb.start, mark.angle), G.point(climb.endInclusive, mark.angle), CLIMB, StrokeCap.Round)
                }
            }
            drawLine(colors.ink2, Offset(G.CENTRE - CROSS_HALF, G.CENTRE), Offset(G.CENTRE + CROSS_HALF, G.CENTRE), CROSS)
            drawLine(colors.ink2, Offset(G.CENTRE, G.CENTRE - CROSS_HALF), Offset(G.CENTRE, G.CENTRE + CROSS_HALF), CROSS)
        }
        marks.forEachIndexed { i, mark ->
            val hue = hues[i]
            val style = if (mark.rim && hue != null) type.discNumeralRim.copy(color = hue) else type.discNumeral.copy(color = colors.ink2)
            val layout = measurer.measure(G.numeral(mark.month), style)
            val at = px(G.point(G.NUMERAL_R, mark.angle))
            drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
            monthCounter(mark, hue, px(G.point(mark.counterR, mark.angle)), G.COUNTER_WIDTH * unit, prints, colors, unit)
        }
    }
}

/** A month's counter alone, the year table's mark (the board's `counterIcon`, 14 px in 28). Decorative. */
@Composable
fun MonthCounter(
    month: AchievementMonth,
    lookups: AchievementLookups,
    modifier: Modifier = Modifier,
    box: Dp = 28.dp,
) {
    val colors = PebblesTheme.spine.colors
    val hue = monthHues(listOf(month), lookups).single()
    val prints = rememberCounterPrints(listOf(hue))
    val mark = remember(month) { G.mark(month) }
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        monthCounter(mark, hue, center, 14f * unit, prints, colors, unit)
    }
}
