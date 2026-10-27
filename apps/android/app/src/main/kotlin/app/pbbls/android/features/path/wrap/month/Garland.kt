package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.DotStyle
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RadialLayout
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.dotMark
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.render.stone.SealPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.week.valenceOf

private val GARLAND = 262.dp
private val RADIUS = 103.dp
private val SOCKET = 6.8.dp
private val COURSE = 1.dp
private val COURSE_DASH = 2.dp
private val COURSE_GAP = 3.dp
private val STACK_OFFSET = 10.5.dp
private val NUMERAL_OFFSET = 20.dp
private val TIE = 12.dp
private val SEAL_WIDTHS = listOf(14.dp, 17.dp, 20.dp)

/** How far a popping seal's layer reaches past its box: its rim, half outside the outline, and the antialiasing. */
private val SEAL_LAYER_PAD = 2.dp

/** The ring has 32 slots from the foot, clockwise: day n sits in slot n, and the tie fills slot 0. */
private const val SLOTS = 32

/** How far the dotted course stops short of the tie on each side, in slots. */
private const val COURSE_INSET = 0.55f

/**
 * The cover's garland (#1207, D7): the month's days around a ring from the foot, clockwise, on a
 * dotted course. A day with pebbles shows each as a seal turned along the ring (two on one day
 * step in and out); an empty day shows a socket. The 1st, each Monday and the last day are
 * numbered, and a leaf ties the ring at the foot. [inner] sits in its middle. Decorative: the
 * cover says the month in words below it. In the month's intro (#1242) [reveal] lands each day in
 * turn and draws the course to the newest; settled, the default, it is the garland as ever.
 */
@Composable
internal fun Garland(
    wrap: MonthWrap,
    lookups: WrapLookups,
    modifier: Modifier = Modifier,
    reveal: IntroReveal = IntroReveal.settled,
    inner: @Composable BoxScope.() -> Unit = {},
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val cover = wrap.cover ?: return
    val placed =
        remember(cover) {
            cover.days.flatMap { day ->
                day.pebbles.mapIndexed { i, p -> Placed(day.day, i, day.pebbles.size, p) }
            }
        }
    val prints =
        placed.map { place ->
            key(place.pebble.momentId) {
                val p = place.pebble
                rememberSealPrint(
                    valence = valenceOf(sizeOfIntensity(p.intensity), p.polarity),
                    palette = p.emotionId?.let { lookups.emotion(it)?.palette },
                    glyph = null,
                    knockout = colors.ground,
                    variant = SealVariant.MINI,
                )
            }
        }
    val measurer = rememberTextMeasurer()
    val pebbled = remember(cover) { cover.days.map { it.day }.toSet() }
    val numbered = remember(wrap) { wrap.numberedDays() }
    // The one paint a popping seal's layer fades through, so a frame allocates none.
    val layerPaint = remember { Paint() }

    Box(modifier.size(GARLAND), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.matchParentSize().clearAndSetSemantics {}.drawWithCache {
                // Built once per size and composition: each frame only reads the reveal and draws.
                val ring = RadialLayout(SLOTS, startDeg = 90f)
                val center = size.center
                val r = RADIUS.toPx()
                val start = ring.angleDeg(COURSE_INSET)
                val arcTopLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(2 * r, 2 * r)
                val course =
                    Stroke(COURSE.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(COURSE_DASH.toPx(), COURSE_GAP.toPx())))
                val socket = SOCKET.toPx()
                val dotRing = SpineLayoutTokens.DOT_RING.toPx()
                val sockets =
                    (0 until wrap.daysInMonth).filter { it !in pebbled }.map { day ->
                        Socket(IntroKey.Slot(day), ring.point(day + 1, center, r))
                    }
                val pad = SEAL_LAYER_PAD.toPx()
                val seals =
                    placed.mapIndexed { index, place ->
                        val slot = place.day + 1
                        val offset = (place.index - (place.count - 1) / 2f) * -STACK_OFFSET.toPx()
                        val print = prints[index]
                        val w = SEAL_WIDTHS[(place.pebble.intensity - 1).coerceIn(0, 2)].toPx()
                        val h = w / print.aspectRatio
                        SealAt(
                            // A day's first seal lands on its slot's beat, a stacked one on its own.
                            key = if (place.index == 0) IntroKey.Slot(place.day) else IntroKey.Seal(place.day, place.index),
                            at = ring.point(slot, center, r + offset),
                            turn = ring.angleDeg(slot) + 90f,
                            print = print,
                            width = w,
                            height = h,
                            layer = Rect(-pad, -pad, w + pad, h + pad),
                        )
                    }
                val style = type.dayLetter.copy(color = colors.ink3)
                val numerals =
                    numbered.map { day ->
                        val stacked =
                            cover.days
                                .firstOrNull { it.day == day }
                                ?.pebbles
                                ?.size ?: 0
                        val at = ring.point(day + 1, center, r + NUMERAL_OFFSET.toPx() + if (stacked > 1) STACK_OFFSET.toPx() / 2 else 0f)
                        val text = measurer.measure((day + 1).toString(), style)
                        Numeral(IntroKey.Numeral(day), text, Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
                    }
                // The tie: a leaf at the foot, where the course parts.
                val foot = ring.point(0, center, r)
                val leaf = TIE.toPx()
                val leafPath =
                    Path().apply {
                        moveTo(foot.x, foot.y - leaf / 2)
                        quadraticTo(foot.x + leaf / 2, foot.y, foot.x, foot.y + leaf / 2)
                        quadraticTo(foot.x - leaf / 2, foot.y, foot.x, foot.y - leaf / 2)
                    }
                val veinTop = Offset(foot.x, foot.y - leaf / 2.6f)
                val veinFoot = Offset(foot.x, foot.y + leaf / 2.6f)
                val vein = COURSE.toPx()

                onDrawBehind {
                    // The course, only as far as the newest day while they land.
                    val drawn = reveal(IntroKey.Course)
                    if (drawn > 0f) {
                        drawArc(
                            color = colors.rule,
                            startAngle = start,
                            sweepAngle = Garland.courseSweep(drawn, reveal(IntroKey.Leaf), wrap.daysInMonth),
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = course,
                        )
                    }
                    sockets.forEach { (key, at) ->
                        val shown = reveal(key)
                        if (shown <= 0f) return@forEach
                        if (shown < 1f) {
                            val p = shown.toDouble()
                            val color = colors.ink3.copy(alpha = colors.ink3.alpha * RevealMotion.popOpacity(p).toFloat())
                            dotMark(at, socket * RevealMotion.popScale(p).toFloat(), DotStyle.SOCKET, color, dotRing)
                        } else {
                            dotMark(at, socket, DotStyle.SOCKET, colors.ink3, dotRing)
                        }
                    }
                    seals.forEach { seal ->
                        val shown = reveal(seal.key)
                        if (shown <= 0f) return@forEach
                        withTransform({
                            translate(seal.at.x, seal.at.y)
                            rotate(seal.turn, Offset.Zero)
                            if (shown < 1f) {
                                val pop = RevealMotion.popScale(shown.toDouble()).toFloat()
                                scale(pop, pop, Offset.Zero)
                            }
                            translate(-seal.width / 2, -seal.height / 2)
                        }) {
                            val opacity = RevealMotion.popOpacity(shown.toDouble()).toFloat()
                            if (opacity < 1f) {
                                // The seal overlaps its own paths, so it fades as one: a layer, only while it fades in.
                                layerPaint.alpha = opacity
                                drawContext.canvas.saveLayer(seal.layer, layerPaint)
                                drawSeal(seal.print, seal.width)
                                drawContext.canvas.restore()
                            } else {
                                drawSeal(seal.print, seal.width)
                            }
                        }
                    }
                    numerals.forEach { (key, text, topLeft) ->
                        val shown = reveal(key)
                        if (shown <= 0f) return@forEach
                        if (shown < 1f) {
                            drawText(text, topLeft = topLeft, alpha = RevealMotion.easeOut(shown.toDouble()).toFloat())
                        } else {
                            drawText(text, topLeft = topLeft)
                        }
                    }
                    val tied = reveal(IntroKey.Leaf)
                    if (tied > 0f) {
                        if (tied < 1f) {
                            val pop = RevealMotion.popScale(tied.toDouble()).toFloat()
                            val alpha = RevealMotion.popOpacity(tied.toDouble()).toFloat()
                            withTransform({ scale(pop, pop, foot) }) {
                                drawPath(leafPath, colors.ink2, alpha = alpha)
                                drawLine(colors.ground, veinTop, veinFoot, vein, alpha = alpha)
                            }
                        } else {
                            drawPath(leafPath, colors.ink2)
                            drawLine(colors.ground, veinTop, veinFoot, vein)
                        }
                    }
                }
            },
        )
        inner()
    }
}

/** The garland's pure geometry (iOS `Garland.courseSweep`). */
internal object Garland {
    /**
     * The course's sweep from its start, in degrees: while the days land, half a slot past the newest;
     * once they have, on to its end as the leaf ties the ring (a month under 31 days leaves that much to
     * draw). Settled, exactly the whole course.
     */
    fun courseSweep(
        course: Float,
        leaf: Float,
        daysInMonth: Int,
    ): Float {
        val ring = RadialLayout(SLOTS, startDeg = 90f)
        val start = ring.angleDeg(COURSE_INSET)
        val full = ring.angleDeg(SLOTS - COURSE_INSET) - start
        val span = maxOf(daysInMonth - 1, 0)

        fun newest(drawn: Float) = minOf(full, ring.angleDeg(1.5f + RevealMotion.easeInOut(drawn.toDouble()).toFloat() * span) - start)
        if (course < 1f) return newest(course)
        if (leaf >= 1f) return full
        val last = newest(1f)
        return last + (full - last) * RevealMotion.easeOut(leaf.toDouble()).toFloat()
    }
}

/** The 1st, each Monday and the last day: the days the garland numbers. */
internal fun MonthWrap.numberedDays(): List<Int> =
    (0 until daysInMonth).filter { d ->
        d == 0 ||
            d == daysInMonth - 1 ||
            (firstWeekday.value - 1 + d) % 7 == 0
    }

private class Placed(
    val day: Int,
    val index: Int,
    val count: Int,
    val pebble: CoverPebble,
)

/** An empty day's socket on the ring: its beat's key and its centre. */
private data class Socket(
    val key: IntroKey,
    val at: Offset,
)

/** A seal on the ring: its beat's key, its centre and turn, its print and size, and the box its fading layer covers. */
private class SealAt(
    val key: IntroKey,
    val at: Offset,
    val turn: Float,
    val print: SealPrint,
    val width: Float,
    val height: Float,
    val layer: Rect,
)

/** A day's numeral: its beat's key, its laid-out text and where it sits. */
private data class Numeral(
    val key: IntroKey,
    val text: TextLayoutResult,
    val topLeft: Offset,
)
