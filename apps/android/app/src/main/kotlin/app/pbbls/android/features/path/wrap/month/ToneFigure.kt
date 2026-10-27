package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

private val UNIT = 8.dp
private val MAX_SIDE = 64.dp
private val TOP = 14.dp
private val AXIS = 30.dp
private val BAR = 5.2.dp
private val BAR_GAP = 3.dp
private val EVEN_BASE = 2.4.dp
private val EVEN_STEP = 1.25.dp
private val QUIET = 1.dp
private val LINE = 1.dp
private val INSET = 10.dp
private const val MIN_SCALE = 3

/**
 * The tone of the month (#1207, D11): for each day, a bar up for bright and a bar down for heavy, as
 * tall as their summed intensities, and a circle on the line for even, growing with its own. The
 * sentence reads the server's verdict and the month's first and last pebbles.
 */
@Composable
internal fun ToneFigure(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.TONE, wrap, lookups)) return
    val tone = requireNotNull(wrap.tone)
    val days = tone.days
    val maxUp = maxOf(MIN_SCALE, days.maxOf { it.up })
    val maxDown = maxOf(MIN_SCALE, days.maxOf { it.down })
    // A heavy month keeps the figure within reach: the unit shrinks, never the proportions.
    val unit: Dp = min(UNIT, MAX_SIDE / maxOf(maxUp, maxDown))
    val height = TOP + unit * maxUp + unit * maxDown + AXIS
    val line = copy.toneLine(tone.verdict, tone.first, tone.last)
    val measurer = rememberTextMeasurer()
    val numbered =
        (0 until wrap.daysInMonth).filter { d ->
            d == 0 ||
                d == wrap.daysInMonth - 1 ||
                (wrap.firstWeekday.value - 1 + d) % 7 == 0
        }

    Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
        if (!onCard) {
            val head = MonthSections.head(MonthSection.TONE, wrap, lookups, copy)
            SpineSectionHeader(label = head.label, hint = head.hint)
        }
        Text(line, style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = line }) {
            val u = unit.toPx()
            val y0 = (TOP + unit * maxUp).toPx()
            val inset = INSET.toPx()
            val step = if (days.size > 1) (size.width - 2 * inset) / (days.size - 1) else 0f
            val bar = BAR.toPx()
            drawLine(colors.rule, Offset(4.dp.toPx(), y0), Offset(size.width - 4.dp.toPx(), y0), LINE.toPx())
            days.forEachIndexed { i, d ->
                val x = inset + i * step
                if (d.up >
                    0
                ) {
                    drawRoundRect(
                        colors.ink,
                        Offset(x - bar / 2, y0 - BAR_GAP.toPx() - d.up * u),
                        Size(bar, d.up * u),
                        CornerRadius(
                            bar / 2,
                        ),
                    )
                }
                if (d.down >
                    0
                ) {
                    drawRoundRect(colors.ink3, Offset(x - bar / 2, y0 + BAR_GAP.toPx()), Size(bar, d.down * u), CornerRadius(bar / 2))
                }
                when {
                    d.even > 0 ->
                        drawCircle(
                            colors.ink2,
                            (EVEN_BASE + EVEN_STEP * d.even).toPx(),
                            Offset(x, y0),
                            style = Stroke(LINE.toPx()),
                        )
                    d.up == 0 && d.down == 0 -> drawCircle(colors.rule, QUIET.toPx(), Offset(x, y0))
                }
            }
            numbered.forEach { d ->
                val t = measurer.measure((d + 1).toString(), type.dayLetter.copy(color = colors.ink3))
                drawText(t, topLeft = Offset(inset + d * step - t.size.width / 2f, size.height - t.size.height - 2.dp.toPx()))
            }
            val bright = measurer.measure(copy.polarityWord(ValencePolarity.HIGHLIGHT), type.dayLetter.copy(color = colors.ink3))
            val heavy = measurer.measure(copy.polarityWord(ValencePolarity.LOWLIGHT), type.dayLetter.copy(color = colors.ink3))
            drawText(bright, topLeft = Offset(4.dp.toPx(), 0f))
            drawText(heavy, topLeft = Offset(4.dp.toPx(), y0 + maxDown * u))
        }
    }
}
