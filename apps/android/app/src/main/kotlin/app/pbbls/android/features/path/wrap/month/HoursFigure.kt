package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RadialLayout
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.hours
import app.pbbls.android.features.path.wrap.intro.insightsHead
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import kotlin.math.ceil
import kotlin.math.max

private val HEIGHT = 296.dp
private val OUTER = 112.dp
private val INNER = 78.dp
private val LANE_OUT = 10.5.dp
private val LANE_IN = 5.4.dp
private val STONE_OFF = 8.dp
private val DOT_OFF = 5.dp
private val DOT = 2.1.dp
private val RING = 1.dp
private val WAIT = 0.8.dp
private val LABEL_OFF = 20.dp
private const val NIGHT_ALPHA = 0.35f
private const val WAIT_ALPHA = 0.6f

/**
 * The hours (#1207, D12): a 24-hour dial, noon on top. Outside, each stone on the hour it happened;
 * inside, a dot on the hour it was collected; a spiral between them is the wait; the night (18:00 to
 * 06:00) a wash between the rings; in the middle, the server's busiest four hours.
 */
@Composable
internal fun HoursFigure(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.HOURS, wrap, lookups)) return
    val hours = requireNotNull(wrap.hours)
    val items = hours.items.mapNotNull { mark -> lookups.moment(mark.momentId)?.let { mark to it } }
    val prints =
        items.map { (mark, moment) ->
            key(mark.momentId) { rememberSealPrint(moment.valence, lookups.paletteFor(moment), null, colors.ground, SealSize.TINY.variant) }
        }
    val widths = items.map { (_, moment) -> SealSize.TINY.width(moment.valence.sizeGroup) }
    val measurer = rememberTextMeasurer()
    val busiest = hours.busiest
    val description = busiest?.let { copy.hoursDescription(it.count, items.size, it.start) } ?: copy.hoursSub()

    Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
        if (!onCard) {
            val head = MonthSections.head(MonthSection.HOURS, wrap, lookups, copy)
            SpineSectionHeader(label = head.label, hint = head.hint, modifier = Modifier.padding(top = 28.dp))
        }
        Text(copy.hoursSub(), style = type.still, color = colors.ink2, modifier = Modifier.padding(top = 6.dp))
        Box(Modifier.fillMaxWidth().height(HEIGHT).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(HEIGHT).clearAndSetSemantics {}) {
                val dial = RadialLayout.continuous(24, top = 12f)
                val ro = OUTER.toPx()
                val ri = INNER.toPx()

                fun at(
                    hour: Double,
                    r: Float,
                ) = dial.point(hour.toFloat(), center, r)

                // The night, from 18:00 round the foot to 06:00.
                val night =
                    Path().apply {
                        arcTo(Rect(center, ro), dial.angleDeg(18f), 180f, true)
                        arcTo(Rect(center, ri), dial.angleDeg(6f), -180f, false)
                        close()
                    }
                drawPath(night, colors.rule, alpha = NIGHT_ALPHA)
                drawCircle(colors.rule, ro, center, style = Stroke(RING.toPx()))
                drawCircle(colors.rule, ri, center, style = Stroke(RING.toPx()))
                for (h in 0 until 24 step 3) {
                    val big = if (h % 6 == 0) 4.dp.toPx() else 2.5.dp.toPx()
                    drawLine(colors.ink3, at(h.toDouble(), ro - big), at(h.toDouble(), ro + big), RING.toPx())
                }
                listOf(0, 6, 12, 18).forEach { h ->
                    val t = measurer.measure(h.toString(), type.dayLetter.copy(color = colors.ink3))
                    val p = at(h.toDouble(), ro + LABEL_OFF.toPx())
                    drawText(t, topLeft = Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f))
                }
                // The waits: a spiral from the stone's hour, clockwise, to the collection's.
                items.forEach { (mark, _) ->
                    val lag = HoursLayout.forward(mark.happened, mark.collected)
                    val n = max(2, ceil(lag * 3).toInt())
                    val r0 = ro - 1.5.dp.toPx()
                    val r1 = ri + 1.5.dp.toPx()
                    val path = Path()
                    for (i in 0..n) {
                        val t = i.toDouble() / n
                        val p = at(mark.happened + lag * t, (r0 + (r1 - r0) * t).toFloat())
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, colors.ink3, alpha = WAIT_ALPHA, style = Stroke(WAIT.toPx(), cap = StrokeCap.Round))
                }
                val dot = DOT.toPx()
                val inLanes =
                    HoursLayout.lanes(items.map { it.first.collected }, items.map { dot * 2 + 0.6f }) { lane ->
                        (
                            2 * Math.PI *
                                (ri - DOT_OFF.toPx() - lane * LANE_IN.toPx()) /
                                24
                        ).toFloat()
                    }
                items.forEachIndexed { i, (mark, _) ->
                    drawCircle(
                        colors.ink2,
                        dot,
                        at(
                            mark.collected,
                            ri - DOT_OFF.toPx() - inLanes[i] * LANE_IN.toPx(),
                        ),
                    )
                }
                val outLanes =
                    HoursLayout.lanes(items.map { it.first.happened }, widths.map { it.toPx() + 1 }) { lane ->
                        (
                            2 * Math.PI *
                                (ro + STONE_OFF.toPx() + lane * LANE_OUT.toPx()) /
                                24
                        ).toFloat()
                    }
                items.forEachIndexed { i, (mark, _) ->
                    val p = at(mark.happened, ro + STONE_OFF.toPx() + outLanes[i] * LANE_OUT.toPx())
                    val w = widths[i].toPx()
                    val h = w / prints[i].aspectRatio
                    withTransform({
                        translate(p.x, p.y)
                        rotate(((mark.happened - 12) / 24 * 360).toFloat(), Offset.Zero)
                        translate(-w / 2, -h / 2)
                    }) { drawSeal(prints[i], w) }
                }
            }
            if (busiest != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(busiest.count.toString(), style = type.dayNumeral, color = colors.ink)
                    Text(copy.hoursOf(items.size), style = type.dayWeekday, color = colors.ink2, textAlign = TextAlign.Center)
                    Text(copy.hoursWindow(busiest.start), style = type.contentsTime, color = colors.ink2)
                }
            }
        }
    }
}

/** The hours' pure parts (#1242; iOS `HoursFigure`'s statics). */
internal object HoursFigure {
    /** The hours' intro, with the insights head's when it leads them; none when nothing shows. */
    fun introBeats(
        wrap: MonthWrap,
        lookups: WrapLookups,
        insights: Boolean,
    ): List<IntroBeat> {
        val marks = wrap.hours?.items.orEmpty()
        val stones = marks.count { lookups.moment(it.momentId) != null }
        if (stones == 0) return if (insights) IntroScore.insightsHead() else emptyList()
        return IntroScore.hours(stones, insights)
    }
}
