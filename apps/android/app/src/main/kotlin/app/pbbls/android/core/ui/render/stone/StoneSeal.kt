package app.pbbls.android.core.ui.render.stone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup

/** `MINI` for the contents and the card index (20–30 dp); `PRINT` larger. */
enum class SealVariant { MINI, PRINT }

/**
 * The stone as a flat print (#1122, spec §The seal): wherever it is read
 * rather than pressed. A high moment prints in relief (solid ink, the glyph
 * cut back to the paper), an even one as an open line on a pale tint, a low
 * one engraved (hatched in the deeper ink). It prints in the pebble's spot ink,
 * `primary` on Paper and `secondary` at Night; [knockout] is what the glyph
 * is cut back to, the ground unless a card sets its stock. Drawn directly: it
 * is small and flat. The caller sets the width; the height follows the
 * silhouette. Decorative.
 */
@Composable
fun StoneSeal(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    knockout: Color,
    modifier: Modifier = Modifier,
    variant: SealVariant = SealVariant.MINI,
) {
    val colors = PebblesTheme.spine.colors
    val blank = palette == null
    val size = valence.sizeGroup
    val polarity = stonePolarity(valence.polarity, blank)
    // A moment with no emotion yet prints as a blank, uncarved river pebble.
    val carved = if (blank) null else glyph
    val outline = rememberStoneOutline(size, polarity)
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val night = colors.isNight
    val inks =
        remember(stone, night, colors.ground, knockout) {
            val spot = StoneTones.spot(stone, night)
            val deep = StoneTones.deep(stone, colors.ground, night)
            SealInks(
                spot = spot,
                deep = deep,
                tint = StoneTones.mixOklab(spot, NEUTRAL_TINT, knockout),
                wash = StoneTones.mixOklab(deep, LOWLIGHT_WASH, knockout),
            )
        }
    val glyphLines =
        remember(carved, size, polarity) {
            carved?.let { sealGlyphLines(it, size, polarity) }.orEmpty()
        }
    val hatch = remember(size, polarity) { if (polarity == ValencePolarity.LOWLIGHT) hatchPath(size) else null }
    val canvasAffine = remember(size, polarity) { StoneGeometry.canvasTransform(size, polarity) }
    val weight = WEIGHTS.getValue(variant)
    val box = StoneGeometry.outlineBox(size)

    Canvas(modifier.aspectRatio(box.width / box.height)) {
        val pxPerUnit = this.size.width / box.width
        val body = outline.path
        withTransform({ scale(pxPerUnit, pxPerUnit, Offset.Zero) }) {
            when (polarity) {
                ValencePolarity.HIGHLIGHT -> {
                    drawPath(body, inks.spot)
                    drawPath(body, inks.spot, style = Stroke(HIGHLIGHT_RIM.toPx() / pxPerUnit))
                    drawGlyph(glyphLines, knockout, weight.glyph, pxPerUnit)
                }
                ValencePolarity.NEUTRAL -> {
                    drawPath(body, inks.tint)
                    drawGlyph(glyphLines, inks.spot, weight.glyph, pxPerUnit)
                    drawPath(body, inks.spot, style = Stroke(weight.rim.toPx() / pxPerUnit))
                }
                ValencePolarity.LOWLIGHT -> {
                    drawPath(body, inks.wash)
                    if (hatch != null) {
                        clipPath(body) {
                            withTransform({ transform(canvasAffine.toComposeMatrix()) }) {
                                drawPath(
                                    hatch,
                                    inks.deep,
                                    alpha = HATCH_ALPHA,
                                    style = Stroke(HATCH_STROKE.toPx() / (pxPerUnit * canvasAffine.a)),
                                )
                            }
                        }
                    }
                    drawGlyph(glyphLines, knockout, weight.halo, pxPerUnit)
                    drawGlyph(glyphLines, inks.deep, weight.glyph, pxPerUnit)
                    drawPath(body, inks.deep, style = Stroke(weight.rim.toPx() / pxPerUnit))
                }
            }
        }
    }
}

/** Line weights: the glyph, the glyph's knock-out halo, the rim. */
private class SealWeights(
    val glyph: Dp,
    val halo: Dp,
    val rim: Dp,
)

private val WEIGHTS: Map<SealVariant, SealWeights> =
    mapOf(
        SealVariant.MINI to SealWeights(glyph = 1.dp, halo = 2.6.dp, rim = 1.1.dp),
        SealVariant.PRINT to SealWeights(glyph = 1.4.dp, halo = 3.6.dp, rim = 1.3.dp),
    )

private class SealInks(
    val spot: Color,
    val deep: Color,
    val tint: Color,
    val wash: Color,
)

/** A glyph stroke's cut line in outline units, with its width relative to the engine's. */
private class SealGlyphLine(
    val path: Path,
    val weightScale: Float,
)

private val HIGHLIGHT_RIM = 1.dp
private val HATCH_STROKE = 0.8.dp
private const val HATCH_ALPHA = 0.85f
private const val HATCH_STEP = 30
private const val HATCH_BLEED = 20
private const val NEUTRAL_TINT = 0.15f
private const val LOWLIGHT_WASH = 0.09f

/** The engine draws strokes at this width; a thinner glyph stroke keeps its proportion. */
private const val ENGINE_STROKE_WIDTH = 6.0

private fun sealGlyphLines(
    glyph: PebbleGlyph,
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
): List<SealGlyphLine> {
    val side = StoneGeometry.glyphSide(glyph.viewBox)
    val place =
        StoneGeometry
            .carveTransform(size, polarity)
            .concat(StoneGeometry.glyphTransform(size, polarity, side))
    return glyph.strokes.mapNotNull { stroke ->
        StonePainter.glyphLine(stroke.d, side)?.let { line ->
            SealGlyphLine(
                path = line.asComposePath().transformed(place),
                weightScale = (stroke.width / ENGINE_STROKE_WIDTH).toFloat(),
            )
        }
    }
}

/** 45° hatching across the canvas, 30 units apart: the engraved seal's shading (web `StoneSprite.hatch`). */
private fun hatchPath(size: ValenceSizeGroup): Path {
    val canvas = StoneGeometry.SEED_CANVAS.getValue(size)
    val w = canvas.width.toInt()
    val h = canvas.height.toInt()
    return Path().apply {
        var x = -(h + HATCH_BLEED)
        while (x <= w) {
            moveTo(x.toFloat(), -HATCH_BLEED.toFloat())
            lineTo((x + h + 2 * HATCH_BLEED).toFloat(), (h + HATCH_BLEED).toFloat())
            x += HATCH_STEP
        }
    }
}

/** Inside the outline-unit transform: strokes at a constant on-screen [weight], round caps and joins. */
private fun DrawScope.drawGlyph(
    lines: List<SealGlyphLine>,
    color: Color,
    weight: Dp,
    pxPerUnit: Float,
) {
    if (lines.isEmpty()) return
    val weightPx = weight.toPx()
    lines.forEach { line ->
        drawPath(
            line.path,
            color,
            style =
                Stroke(
                    width = weightPx * line.weightScale / pxPerUnit,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}
