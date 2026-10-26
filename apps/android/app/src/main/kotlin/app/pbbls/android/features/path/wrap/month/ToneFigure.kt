package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.insightsHead
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.intro.tone
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
 *
 * In the month intro (#1242) the sentence rises, then the whole chart is swept in from the left, left to
 * right whatever the language (the days run that way, as iOS's mask does), and each even day's circle
 * pulses as the sweep reaches it. Settled, it draws as ever.
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
    val reveal = currentIntroReveal()
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
    val brightWord = copy.polarityWord(ValencePolarity.HIGHLIGHT)
    val heavyWord = copy.polarityWord(ValencePolarity.LOWLIGHT)

    Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
        if (!onCard) {
            val head = MonthSections.head(MonthSection.TONE, wrap, lookups, copy)
            SpineSectionHeader(label = head.label, hint = head.hint)
        }
        Text(
            line,
            style = type.still,
            color = colors.ink2,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).introReveal(reveal, IntroKey.Lead),
        )
        Spacer(
            Modifier.fillMaxWidth().height(height).semantics { contentDescription = line }.drawWithCache {
                // Built once per size and composition: a frame of the intro only reads the reveal and draws.
                val u = unit.toPx()
                val y0 = (TOP + unit * maxUp).toPx()
                val inset = INSET.toPx()
                val step = if (days.size > 1) (size.width - 2 * inset) / (days.size - 1) else 0f
                val bar = BAR.toPx()
                val gap = BAR_GAP.toPx()
                val stroke = LINE.toPx()
                val ring = Stroke(stroke)
                val lineInset = 4.dp.toPx()
                val words = type.dayLetter.copy(color = colors.ink3)
                val numerals =
                    numbered.map { d ->
                        val t = measurer.measure((d + 1).toString(), words)
                        t to Offset(inset + d * step - t.size.width / 2f, size.height - t.size.height - 2.dp.toPx())
                    }
                val bright = measurer.measure(brightWord, words)
                val heavy = measurer.measure(heavyWord, words)
                // The even days, each with its beat's key, its place on the line and its circle's radius.
                val evens =
                    days.withIndex().filter { it.value.even > 0 }.map { (i, d) ->
                        EvenDay(IntroKey.Day(i), Offset(inset + i * step, y0), (EVEN_BASE + EVEN_STEP * d.even).toPx())
                    }

                fun DrawScope.chart() {
                    drawLine(colors.rule, Offset(lineInset, y0), Offset(size.width - lineInset, y0), stroke)
                    days.forEachIndexed { i, d ->
                        val x = inset + i * step
                        if (d.up > 0) {
                            drawRoundRect(colors.ink, Offset(x - bar / 2, y0 - gap - d.up * u), Size(bar, d.up * u), CornerRadius(bar / 2))
                        }
                        if (d.down > 0) {
                            drawRoundRect(colors.ink3, Offset(x - bar / 2, y0 + gap), Size(bar, d.down * u), CornerRadius(bar / 2))
                        }
                        when {
                            d.even > 0 -> drawCircle(colors.ink2, (EVEN_BASE + EVEN_STEP * d.even).toPx(), Offset(x, y0), style = ring)
                            d.up == 0 && d.down == 0 -> drawCircle(colors.rule, QUIET.toPx(), Offset(x, y0))
                        }
                    }
                    numerals.forEach { (t, at) -> drawText(t, topLeft = at) }
                    drawText(bright, topLeft = Offset(lineInset, 0f))
                    drawText(heavy, topLeft = Offset(lineInset, y0 + maxDown * u))
                    // An even day's pulse as the sweep reaches it: a ring growing out of its circle and fading.
                    evens.forEach { even ->
                        val t = reveal(even.key)
                        if (t > 0f && t < 1f) {
                            drawCircle(colors.ink2, even.radius * (1 + t), even.at, alpha = 1 - t, style = Stroke(stroke * (1 + t)))
                        }
                    }
                }

                onDrawBehind {
                    // Hidden before the sweep, shown up to it while it runs, untouched once it has passed.
                    val sweep = reveal(IntroKey.Sweep)
                    if (sweep >= 1f) {
                        chart()
                    } else if (sweep > 0f) {
                        val eased = RevealMotion.easeInOut(sweep.toDouble()).toFloat()
                        clipRect(right = ToneFigure.sweepWidth(eased, size.width, inset, bar)) { chart() }
                    }
                }
            },
        )
    }
}

/** An even day in the intro: its beat's key, its circle's centre on the line and its radius. */
private class EvenDay(
    val key: IntroKey,
    val at: Offset,
    val radius: Float,
)

/** The tone's pure parts (#1242; iOS `ToneFigure`'s statics). */
internal object ToneFigure {
    /** The tone's intro, with the insights head's when it leads them; none when nothing shows. */
    fun introBeats(
        wrap: MonthWrap,
        insights: Boolean,
    ): List<IntroBeat> {
        val days = wrap.tone?.days.orEmpty()
        if (days.isEmpty()) return if (insights) IntroScore.insightsHead() else emptyList()
        return IntroScore.tone(days, insights)
    }

    /**
     * How much of the chart the sweep shows at [eased] progress (iOS `ToneMetrics.sweepWidth`): the days run
     * from [inset] to `width - inset`, and the sweep leads the day it reaches by half a [bar].
     */
    fun sweepWidth(
        eased: Float,
        width: Float,
        inset: Float,
        bar: Float,
    ): Float = inset + eased * (width - 2 * inset) + bar / 2
}
