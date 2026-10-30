package app.pbbls.android.core.ui.render.stone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine

/**
 * `MINI` for the contents and the card index (20–30 dp); `PRINT` larger. `TINY` (under ~12 dp, #1207)
 * drops the glyph and keeps the engraving's hatch at a fixed on-screen spacing, so a small heavy stone
 * never fills in solid and reads as bright. `PIP` is the bare silhouette in the spot ink (deep when low).
 */
enum class SealVariant { MINI, PRINT, TINY, PIP }

/**
 * The seal's size presets (#1207): one width per stone size, so every surface prints the same stone
 * at the same size. [CONTENTS] is the contents row (20 / 24 / 27), [MILESTONE] the month's milestones,
 * [TINY] the garland, the dial and the tiles, [PIP] a flat silhouette in a key or a tile.
 */
enum class SealSize(
    private val small: Dp,
    private val medium: Dp,
    private val large: Dp,
    val variant: SealVariant,
) {
    CONTENTS(20.dp, 24.dp, 27.dp, SealVariant.MINI),
    MILESTONE(24.dp, 28.dp, 34.dp, SealVariant.MINI),
    TINY(8.5.dp, 10.dp, 11.5.dp, SealVariant.TINY),
    PIP(8.dp, 10.5.dp, 13.dp, SealVariant.PIP),
    ;

    fun width(group: ValenceSizeGroup): Dp =
        when (group) {
            ValenceSizeGroup.SMALL -> small
            ValenceSizeGroup.MEDIUM -> medium
            ValenceSizeGroup.LARGE -> large
        }
}

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
    val print = rememberSealPrint(valence, palette, glyph, knockout, variant)
    Canvas(modifier.aspectRatio(print.aspectRatio)) { drawSeal(print, size.width) }
}

/** The seal at a preset [size]: its width for the stone's size, and the preset's print. */
@Composable
fun StoneSeal(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    knockout: Color,
    size: SealSize,
    modifier: Modifier = Modifier,
) = StoneSeal(valence, palette, glyph, knockout, modifier.width(size.width(valence.sizeGroup)), size.variant)

/**
 * Everything a seal needs to print, resolved once, so a `Canvas` that draws many stones (the month's
 * garland, the hours dial) calls [drawSeal] without composing a `StoneSeal` per stone.
 */
@Stable
class SealPrint internal constructor(
    internal val outline: Path,
    internal val polarity: ValencePolarity,
    internal val inks: SealInks,
    internal val knockout: Color,
    internal val glyphLines: List<SealGlyphLine>,
    internal val hatch: Path?,
    internal val size: ValenceSizeGroup,
    internal val canvasAffine: Affine,
    internal val variant: SealVariant,
    internal val boxWidth: Float,
    val aspectRatio: Float,
    /** Only the rim, on the knockout: the blank and dotted ink prints (#1307). */
    internal val rimOnly: Boolean = false,
    internal val dotted: Boolean = false,
)

@Composable
fun rememberSealPrint(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    knockout: Color,
    variant: SealVariant = SealVariant.MINI,
): SealPrint {
    val colors = PebblesTheme.spine.colors
    val blank = palette == null
    val size = valence.sizeGroup
    val polarity = stonePolarity(valence.polarity, blank)
    // A moment with no emotion yet prints as a blank, uncarved river pebble; the small prints carry no glyph.
    val carved = if (blank || variant == SealVariant.TINY || variant == SealVariant.PIP) null else glyph
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
    val hatch = remember(size, polarity) { if (polarity == ValencePolarity.LOWLIGHT) hatchPath(size, HATCH_STEP.toFloat()) else null }
    val canvasAffine = remember(size, polarity) { StoneGeometry.canvasTransform(size, polarity) }
    val box = StoneGeometry.outlineBox(size)
    return remember(outline, polarity, inks, knockout, glyphLines, hatch, canvasAffine, variant) {
        SealPrint(outline.path, polarity, inks, knockout, glyphLines, hatch, size, canvasAffine, variant, box.width, box.width / box.height)
    }
}

/**
 * How an ink seal prints (#1307): the Spine's three prints, plus the board's blank rim (a moment,
 * a quiet month) and its dotted rim (a month still to come).
 */
enum class InkPrint(
    internal val polarity: ValencePolarity,
) {
    RELIEF(ValencePolarity.HIGHLIGHT),
    OPEN(ValencePolarity.NEUTRAL),
    ENGRAVED(ValencePolarity.LOWLIGHT),
    BLANK(ValencePolarity.NEUTRAL),
    DOTTED(ValencePolarity.NEUTRAL),
}

/** The print for a valence's polarity, in ink. */
val ValencePolarity.inkPrint: InkPrint
    get() =
        when (this) {
            ValencePolarity.HIGHLIGHT -> InkPrint.RELIEF
            ValencePolarity.NEUTRAL -> InkPrint.OPEN
            ValencePolarity.LOWLIGHT -> InkPrint.ENGRAVED
        }

/**
 * A seal in [ink] rather than an emotion's spot ink (#1307): the achievements' marks are ink
 * wherever no emotion colours them. It keeps the polarity, unlike a palette-less [rememberSealPrint]
 * (a blank moment), and carries no glyph. Draw it with [drawSeal].
 */
@Composable
fun rememberInkSealPrint(
    size: ValenceSizeGroup,
    print: InkPrint,
    ink: Color,
    knockout: Color,
    variant: SealVariant = SealVariant.MINI,
): SealPrint {
    val polarity = print.polarity
    val outline = rememberStoneOutline(size, polarity)
    val inks =
        remember(ink, knockout) {
            SealInks(
                spot = ink,
                deep = ink,
                tint = StoneTones.mixOklab(ink, NEUTRAL_TINT, knockout),
                wash = StoneTones.mixOklab(ink, LOWLIGHT_WASH, knockout),
            )
        }
    val hatch = remember(size, polarity) { if (polarity == ValencePolarity.LOWLIGHT) hatchPath(size, HATCH_STEP.toFloat()) else null }
    val canvasAffine = remember(size, polarity) { StoneGeometry.canvasTransform(size, polarity) }
    val box = StoneGeometry.outlineBox(size)
    return remember(outline, polarity, inks, knockout, hatch, canvasAffine, variant, print) {
        SealPrint(
            outline.path,
            polarity,
            inks,
            knockout,
            emptyList(),
            hatch,
            size,
            canvasAffine,
            variant,
            box.width,
            box.width / box.height,
            rimOnly = print == InkPrint.BLANK || print == InkPrint.DOTTED,
            dotted = print == InkPrint.DOTTED,
        )
    }
}

/** Prints [print] at the origin, [width] px wide (the height follows its aspect ratio). Move it with `withTransform`. */
fun DrawScope.drawSeal(
    print: SealPrint,
    width: Float,
) {
    val pxPerUnit = width / print.boxWidth
    val body = print.outline
    val inks = print.inks
    val weight = WEIGHTS.getValue(print.variant)
    withTransform({ scale(pxPerUnit, pxPerUnit, Offset.Zero) }) {
        if (print.variant == SealVariant.PIP) {
            drawPath(body, if (print.polarity == ValencePolarity.LOWLIGHT) inks.deep else inks.spot)
            return@withTransform
        }
        if (print.rimOnly) {
            drawPath(body, print.knockout)
            val dash =
                if (print.dotted) {
                    PathEffect.dashPathEffect(floatArrayOf(DOT_ON.toPx() / pxPerUnit, DOT_OFF.toPx() / pxPerUnit))
                } else {
                    null
                }
            drawPath(body, inks.spot, style = Stroke(weight.rim.toPx() / pxPerUnit, pathEffect = dash))
            return@withTransform
        }
        when (print.polarity) {
            ValencePolarity.HIGHLIGHT -> {
                drawPath(body, inks.spot)
                drawPath(body, inks.spot, style = Stroke(HIGHLIGHT_RIM.toPx() / pxPerUnit))
                drawGlyph(print.glyphLines, print.knockout, weight.glyph, pxPerUnit)
            }
            ValencePolarity.NEUTRAL -> {
                drawPath(body, inks.tint)
                drawGlyph(print.glyphLines, inks.spot, weight.glyph, pxPerUnit)
                drawPath(body, inks.spot, style = Stroke(weight.rim.toPx() / pxPerUnit))
            }
            ValencePolarity.LOWLIGHT -> {
                drawPath(body, inks.wash)
                val affine = print.canvasAffine
                // The tiny print keeps its hatch a fixed distance apart on screen (45°, so the step is √2 × the gap).
                val hatch =
                    if (print.variant == SealVariant.TINY) {
                        hatchPath(print.size, TINY_HATCH_GAP.toPx() * SQRT_2 / (pxPerUnit * affine.a))
                    } else {
                        print.hatch
                    }
                if (hatch != null) {
                    clipPath(body) {
                        withTransform({ transform(affine.toComposeMatrix()) }) {
                            drawPath(
                                hatch,
                                inks.deep,
                                alpha = HATCH_ALPHA,
                                style = Stroke(weight.hatch.takeOrElse { HATCH_STROKE }.toPx() / (pxPerUnit * affine.a)),
                            )
                        }
                    }
                }
                drawGlyph(print.glyphLines, print.knockout, weight.halo, pxPerUnit)
                drawGlyph(print.glyphLines, inks.deep, weight.glyph, pxPerUnit)
                drawPath(body, inks.deep, style = Stroke(weight.rim.toPx() / pxPerUnit))
            }
        }
    }
}

/** Line weights: the glyph, the glyph's knock-out halo, the rim. */
private class SealWeights(
    val glyph: Dp,
    val halo: Dp,
    val rim: Dp,
    // Unspecified, not HATCH_STROKE: WEIGHTS is initialised before the constants below it, so a
    // default read here would be 0 dp. Resolved where the hatch is drawn.
    val hatch: Dp = Dp.Unspecified,
)

private val WEIGHTS: Map<SealVariant, SealWeights> =
    mapOf(
        SealVariant.MINI to SealWeights(glyph = 1.dp, halo = 2.6.dp, rim = 1.1.dp),
        SealVariant.PRINT to SealWeights(glyph = 1.4.dp, halo = 3.6.dp, rim = 1.3.dp),
        SealVariant.TINY to SealWeights(glyph = 0.dp, halo = 0.dp, rim = 0.7.dp, hatch = 0.6.dp),
        SealVariant.PIP to SealWeights(glyph = 0.dp, halo = 0.dp, rim = 0.dp),
    )

internal class SealInks(
    val spot: Color,
    val deep: Color,
    val tint: Color,
    val wash: Color,
)

/** A glyph stroke's cut line in outline units, with its width relative to the engine's. */
internal class SealGlyphLine(
    val path: Path,
    val weightScale: Float,
)

private val HIGHLIGHT_RIM = 1.dp
private val HATCH_STROKE = 0.8.dp
private const val HATCH_ALPHA = 0.85f
private const val HATCH_STEP = 30
private const val HATCH_BLEED = 20
private const val NEUTRAL_TINT = 0.15f
private val TINY_HATCH_GAP = 2.4.dp
private const val SQRT_2 = 1.4142135f
private const val LOWLIGHT_WASH = 0.09f

// The board's `stroke-dasharray="1.6 1.7"`: the dotted rim of a month still to come (#1307).
private val DOT_ON = 1.6.dp
private val DOT_OFF = 1.7.dp

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

/** 45° hatching across the canvas, [step] units apart (30 for the web's `StoneSprite.hatch`): the engraved seal's shading. */
private fun hatchPath(
    size: ValenceSizeGroup,
    step: Float,
): Path {
    val canvas = StoneGeometry.SEED_CANVAS.getValue(size)
    val w = canvas.width.toInt()
    val h = canvas.height.toInt()
    return Path().apply {
        var x = -(h + HATCH_BLEED).toFloat()
        while (x <= w) {
            moveTo(x, -HATCH_BLEED.toFloat())
            lineTo(x + h + 2 * HATCH_BLEED, (h + HATCH_BLEED).toFloat())
            x += step
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
