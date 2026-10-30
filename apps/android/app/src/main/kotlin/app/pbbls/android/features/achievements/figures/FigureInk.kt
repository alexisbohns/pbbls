package app.pbbls.android.features.achievements.figures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.rememberInkSealPrint
import app.pbbls.android.core.ui.render.stone.rememberSealPrint
import kotlin.math.roundToInt

/** A family's hue (the board's `famHue`): its primary on Paper, its secondary at Night. */
internal fun EmotionPalette.hue(night: Boolean): Color = if (night) secondary else primary

/** A family's name on the rosace (the board's `tx`): shame's primary is too pale on Paper, so it reads shaded. */
internal fun familyLabelColor(
    slug: String,
    palette: EmotionPalette,
    night: Boolean,
): Color =
    when {
        night -> palette.secondary
        slug == "shame" -> palette.shaded
        else -> palette.primary
    }

/**
 * A lit facet's tone in its family's run (the board's `tone`): at Night it climbs from secondary
 * toward primary (62 % at the run's end); on Paper from secondary toward shaded (8 % to 80 %). A
 * soft one, the star between two lit seats, is 38 % paper.
 */
internal fun seatTone(
    palette: EmotionPalette,
    index: Int,
    familySize: Int,
    night: Boolean,
    stock: Color,
    soft: Boolean,
): Color {
    val t = if (familySize > 1) index.toFloat() / (familySize - 1) else 0.5f
    val base =
        if (night) {
            StoneTones.mixOklab(palette.primary, (t * 62).roundToInt() / 100f, palette.secondary)
        } else {
            StoneTones.mixOklab(palette.shaded, (8 + t * 72).roundToInt() / 100f, palette.secondary)
        }
    return if (soft) StoneTones.mixOklab(stock, 0.38f, base) else base
}

/** "Not yet": a dotted ring in secondary ink on the paper (the board's `ghost`). [unit] is px per board px. */
internal fun DrawScope.ghost(
    center: Offset,
    radius: Float,
    unit: Float,
    colors: SpineColors,
) {
    drawCircle(colors.stock, radius, center)
    drawCircle(
        colors.ink2,
        radius,
        center,
        style = Stroke(width = unit, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * unit, 1.6f * unit))),
    )
}

/** "New since you last looked": a ring around the mark and a dot at its shoulder (the board's `newMark`). */
internal fun DrawScope.newMark(
    center: Offset,
    width: Float,
    unit: Float,
    ink: Color,
) {
    drawCircle(ink, width * 0.86f, center, style = Stroke(unit))
    drawCircle(ink, 1.9f * unit, center + Offset(width * 0.66f, -width * 0.66f))
}

/**
 * How much to scale a text measured at [style] so it stands [boardSize] board units tall in a figure
 * drawn at [unit] px per board unit. The figure's text is its art: it shrinks with the figure on a
 * narrow screen, and since [style]'s size in px carries the font scale, dividing by it keeps the
 * text at the board's size (a figure never draws wider than its board, so [unit] is capped there).
 * The card says the same in words for anyone who reads at a larger size.
 */
internal fun DrawScope.figureTextScale(
    style: TextStyle,
    boardSize: Float,
    unit: Float,
): Float = boardSize * unit / style.fontSize.toPx()

/** [layout] with its middle on [x] and its first baseline on [baseline], scaled by [scale] about that point. */
internal fun DrawScope.drawFigureText(
    layout: TextLayoutResult,
    x: Float,
    baseline: Float,
    scale: Float,
) {
    withTransform({
        translate(x, baseline)
        scale(scale, scale, Offset.Zero)
    }) { drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.firstBaseline)) }
}

/** [layout] centred on [center] both ways (the board's `dominant-baseline: central`), scaled by [scale]. */
internal fun DrawScope.drawFigureTextCentred(
    layout: TextLayoutResult,
    center: Offset,
    scale: Float,
) {
    withTransform({
        translate(center.x, center.y)
        scale(scale, scale, Offset.Zero)
    }) { drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f)) }
}

/** A seal centred on [center], [width] px wide. */
internal fun DrawScope.sealAt(
    print: SealPrint,
    center: Offset,
    width: Float,
) {
    translate(center.x - width / 2, center.y - width / print.aspectRatio / 2) { drawSeal(print, width) }
}

/** One ink seal: a size, how it prints, and the outline it is cut on (the print's own unless the board says otherwise). */
data class InkSeal(
    val size: ValenceSizeGroup,
    val print: InkPrint,
    val outline: ValencePolarity = print.polarity,
)

/** One ink seal in a given colour: ink for the marks, a month's hue for the year's counters. */
internal data class InkKey(
    val seal: InkSeal,
    val ink: Color,
)

/** The ink prints a figure asked for, looked up by key while drawing, never iterated. */
@Stable
internal class InkPrints(
    private val prints: Map<InkKey, SealPrint>,
) {
    fun of(
        seal: InkSeal,
        ink: Color,
    ): SealPrint = prints.getValue(InkKey(seal, ink))
}

/**
 * Only the ink prints [keys] names, each remembered under its own key so the set can change
 * between payloads; the set itself is remembered on the keys and the prints, so a register row
 * builds the one print it draws, not every print there is.
 */
@Composable
internal fun rememberInkPrints(
    keys: List<InkKey>,
    knockout: Color,
    variant: SealVariant,
): InkPrints {
    val distinct = keys.distinct()
    val prints = distinct.map { k -> key(k) { rememberInkSealPrint(k.seal.size, k.seal.print, k.ink, knockout, variant, k.seal.outline) } }
    return remember(distinct, prints) { InkPrints(distinct.zip(prints).toMap()) }
}

/** One emotion seal: a valence printed in a family's palette. */
internal data class SealKey(
    val valence: Valence,
    val palette: EmotionPalette,
)

/**
 * The emotion seals a figure needs, each remembered under its own key, so the set can change
 * between payloads. Looked up by key while drawing, never iterated.
 */
@Composable
internal fun rememberEmotionSeals(
    keys: List<SealKey>,
    knockout: Color,
    variant: SealVariant,
): Map<SealKey, SealPrint> {
    val distinct = keys.distinct()
    val prints = distinct.map { k -> key(k) { rememberSealPrint(k.valence, k.palette, null, knockout, variant) } }
    return remember(distinct, prints) { distinct.zip(prints).toMap() }
}
