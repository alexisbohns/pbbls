package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Paint
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
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.hours
import app.pbbls.android.features.path.wrap.intro.insightsHead
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.intro.ranks
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

/** How far a popping stone's layer reaches past its box: its rim, half outside the outline, and the antialiasing. */
private val SEAL_LAYER_PAD = 2.dp

/**
 * The hours (#1207, D12): a 24-hour dial, noon on top. Outside, each stone on the hour it happened;
 * inside, a dot on the hour it was collected; a spiral between them is the wait; the night (18:00 to
 * 06:00) a wash between the rings; in the middle, the server's busiest four hours.
 *
 * In the month intro (#1242) the sentence rises, the dial's face fades in (its rings, night, ticks, hour
 * numbers and busiest window), then each stone pops with its wait and its dot, in the order of the time
 * of day it happened. Settled, it draws as ever.
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
    val reveal = currentIntroReveal()
    if (!MonthSections.isShown(MonthSection.HOURS, wrap, lookups)) return
    val hours = requireNotNull(wrap.hours)
    val items = hours.items.mapNotNull { mark -> lookups.moment(mark.momentId)?.let { mark to it } }
    val prints =
        items.map { (mark, moment) ->
            key(mark.momentId) { rememberSealPrint(moment.valence, lookups.paletteFor(moment), null, colors.ground, SealSize.TINY.variant) }
        }
    val widths = items.map { (_, moment) -> SealSize.TINY.width(moment.valence.sizeGroup) }
    val measurer = rememberTextMeasurer()
    // The one paint a popping stone's layer fades through, so a frame allocates none.
    val layerPaint = remember { Paint() }
    val busiest = hours.busiest
    val description = busiest?.let { copy.hoursDescription(it.count, items.size, it.start) } ?: copy.hoursSub()

    Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
        if (!onCard) {
            val head = MonthSections.head(MonthSection.HOURS, wrap, lookups, copy)
            SpineSectionHeader(label = head.label, hint = head.hint, modifier = Modifier.padding(top = 28.dp))
        }
        Text(
            copy.hoursSub(),
            style = type.still,
            color = colors.ink2,
            modifier = Modifier.padding(top = 6.dp).introReveal(reveal, IntroKey.Lead),
        )
        Box(Modifier.fillMaxWidth().height(HEIGHT).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxWidth().height(HEIGHT).clearAndSetSemantics {}.drawWithCache {
                    // Built once per size and composition: a frame of the intro only reads the reveal and draws.
                    val dial = RadialLayout.continuous(24, top = 12f)
                    val center = size.center
                    val ro = OUTER.toPx()
                    val ri = INNER.toPx()
                    val ring = Stroke(RING.toPx())

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
                    val ticks =
                        (0 until 24 step 3).map { h ->
                            val big = if (h % 6 == 0) 4.dp.toPx() else 2.5.dp.toPx()
                            at(h.toDouble(), ro - big) to at(h.toDouble(), ro + big)
                        }
                    val numbers =
                        listOf(0, 6, 12, 18).map { h ->
                            val t = measurer.measure(h.toString(), type.dayLetter.copy(color = colors.ink3))
                            val p = at(h.toDouble(), ro + LABEL_OFF.toPx())
                            t to Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f)
                        }
                    // The waits: a spiral from the stone's hour, clockwise, to the collection's.
                    val waits =
                        items.map { (mark, _) ->
                            val lag = HoursLayout.forward(mark.happened, mark.collected)
                            val n = max(2, ceil(lag * 3).toInt())
                            val r0 = ro - 1.5.dp.toPx()
                            val r1 = ri + 1.5.dp.toPx()
                            Path().apply {
                                for (i in 0..n) {
                                    val t = i.toDouble() / n
                                    val p = at(mark.happened + lag * t, (r0 + (r1 - r0) * t).toFloat())
                                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                                }
                            }
                        }
                    val wait = Stroke(WAIT.toPx(), cap = StrokeCap.Round)
                    val dot = DOT.toPx()
                    val inLanes =
                        HoursLayout.lanes(items.map { it.first.collected }, items.map { dot * 2 + 0.6f }) { lane ->
                            (
                                2 * Math.PI *
                                    (ri - DOT_OFF.toPx() - lane * LANE_IN.toPx()) /
                                    24
                            ).toFloat()
                        }
                    val dots = items.mapIndexed { i, (mark, _) -> at(mark.collected, ri - DOT_OFF.toPx() - inLanes[i] * LANE_IN.toPx()) }
                    val outLanes =
                        HoursLayout.lanes(items.map { it.first.happened }, widths.map { it.toPx() + 1 }) { lane ->
                            (
                                2 * Math.PI *
                                    (ro + STONE_OFF.toPx() + lane * LANE_OUT.toPx()) /
                                    24
                            ).toFloat()
                        }
                    val pad = SEAL_LAYER_PAD.toPx()
                    val stones =
                        items.mapIndexed { i, (mark, _) ->
                            val w = widths[i].toPx()
                            val h = w / prints[i].aspectRatio
                            HourStone(
                                at = at(mark.happened, ro + STONE_OFF.toPx() + outLanes[i] * LANE_OUT.toPx()),
                                turn = ((mark.happened - 12) / 24 * 360).toFloat(),
                                width = w,
                                height = h,
                                layer = Rect(-pad, -pad, w + pad, h + pad),
                            )
                        }
                    // Each stone lands by the time of day it happened, with its wait and its dot.
                    val keys = IntroScore.ranks(items.map { it.first.happened }).map { IntroKey.Stone(it) }
                    // Each stone's progress this frame, read once for its wait, its dot and itself.
                    val shown = FloatArray(keys.size)

                    onDrawBehind {
                        // The face lands first: drawn at its eased alpha while it fades in, as ever once landed.
                        val face = reveal(IntroKey.Dial)
                        if (face > 0f) {
                            val alpha = if (face < 1f) RevealMotion.easeOut(face.toDouble()).toFloat() else 1f
                            drawPath(night, colors.rule, alpha = NIGHT_ALPHA * alpha)
                            drawCircle(colors.rule, ro, center, alpha = alpha, style = ring)
                            drawCircle(colors.rule, ri, center, alpha = alpha, style = ring)
                            ticks.forEach { (from, to) -> drawLine(colors.ink3, from, to, RING.toPx(), alpha = alpha) }
                            // Settled: the text's own alpha (NaN is drawText's default).
                            numbers.forEach { (t, topLeft) -> drawText(t, topLeft = topLeft, alpha = if (face < 1f) alpha else Float.NaN) }
                        }
                        keys.forEachIndexed { i, key -> shown[i] = reveal(key) }
                        waits.forEachIndexed { i, path ->
                            val p = shown[i]
                            if (p <= 0f) return@forEachIndexed
                            val alpha = if (p < 1f) RevealMotion.popOpacity(p.toDouble()).toFloat() else 1f
                            drawPath(path, colors.ink3, alpha = WAIT_ALPHA * alpha, style = wait)
                        }
                        dots.forEachIndexed { i, at ->
                            val p = shown[i]
                            if (p <= 0f) return@forEachIndexed
                            val alpha = if (p < 1f) RevealMotion.popOpacity(p.toDouble()).toFloat() else 1f
                            drawCircle(colors.ink2, dot, at, alpha = alpha)
                        }
                        stones.forEachIndexed { i, stone ->
                            val p = shown[i]
                            if (p <= 0f) return@forEachIndexed
                            withTransform({
                                translate(stone.at.x, stone.at.y)
                                rotate(stone.turn, Offset.Zero)
                                if (p < 1f) {
                                    val pop = RevealMotion.popScale(p.toDouble()).toFloat()
                                    scale(pop, pop, Offset.Zero)
                                }
                                translate(-stone.width / 2, -stone.height / 2)
                            }) {
                                val opacity = if (p < 1f) RevealMotion.popOpacity(p.toDouble()).toFloat() else 1f
                                if (opacity < 1f) {
                                    // The stone overlaps its own paths, so it fades as one: a layer, only while it fades in.
                                    layerPaint.alpha = opacity
                                    drawContext.canvas.saveLayer(stone.layer, layerPaint)
                                    drawSeal(prints[i], stone.width)
                                    drawContext.canvas.restore()
                                } else {
                                    drawSeal(prints[i], stone.width)
                                }
                            }
                        }
                    }
                },
            )
            if (busiest != null) {
                Column(
                    Modifier.introReveal(reveal, IntroKey.Dial, IntroLanding.FADE),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(busiest.count.toString(), style = type.dayNumeral, color = colors.ink)
                    Text(copy.hoursOf(items.size), style = type.dayWeekday, color = colors.ink2, textAlign = TextAlign.Center)
                    Text(copy.hoursWindow(busiest.start), style = type.contentsTime, color = colors.ink2)
                }
            }
        }
    }
}

/** A stone on the dial, laid out: its centre, its turn along the ring, its size and its fading layer's bounds. */
private class HourStone(
    val at: Offset,
    val turn: Float,
    val width: Float,
    val height: Float,
    val layer: Rect,
)

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
