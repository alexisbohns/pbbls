package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineColors
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.features.achievements.AchievementLookups

/** One six-firsts mark: its seal and a width in board px. */
data class SixMark(
    val seal: InkSeal,
    val width: Float,
)

/**
 * The board's six-firsts table. The three intensities are a solid print cut on the neutral outline
 * (so Medium and Highlight read apart by shape), the three valences each print on their own.
 */
val SIX_MARKS: Map<StoneKind, SixMark> =
    mapOf(
        StoneKind.SMALL to SixMark(InkSeal(ValenceSizeGroup.SMALL, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 9f),
        StoneKind.MEDIUM to SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 12f),
        StoneKind.LARGE to SixMark(InkSeal(ValenceSizeGroup.LARGE, InkPrint.RELIEF, ValencePolarity.NEUTRAL), 14f),
        StoneKind.LOWLIGHT to SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.ENGRAVED), 12.5f),
        StoneKind.NEUTRAL to SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.OPEN), 12.5f),
        StoneKind.HIGHLIGHT to SixMark(InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF), 12.5f),
    )

/** The six firsts' seals, in the catalog's order: what a figure drawing them asks for. */
internal val SIX_SEALS: List<InkSeal> = StoneKind.entries.map { SIX_MARKS.getValue(it).seal }

private val MOMENT_SEAL = InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.BLANK)
private val PEBBLE_SEAL = InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF)
private val COLLECTION_SEALS =
    listOf(
        InkSeal(ValenceSizeGroup.SMALL, InkPrint.RELIEF),
        InkSeal(ValenceSizeGroup.MEDIUM, InkPrint.OPEN),
        InkSeal(ValenceSizeGroup.SMALL, InkPrint.ENGRAVED),
    )

/** The seals a first-five task's mark prints (a soul and a glyph are drawn, not sealed). */
internal fun firstFiveSeals(step: FirstStep?): List<InkSeal> =
    when (step) {
        FirstStep.MOMENT -> listOf(MOMENT_SEAL)
        FirstStep.PEBBLE -> listOf(PEBBLE_SEAL)
        FirstStep.COLLECTION -> COLLECTION_SEALS
        FirstStep.SOUL, FirstStep.GLYPH, null -> emptyList()
    }

/** Every first-five seal, for a figure that draws all five. */
internal val FIRST_FIVE_SEALS: List<InkSeal> = FirstStep.entries.flatMap(::firstFiveSeals).distinct()

/** An emotion's seal width on the wheel, in board px (`7.4 + intensity × 1.6`). */
fun emotionSealWidth(intensity: Int): Float = 7.4f + intensity * 1.6f

/** A valence's own seal in ink: its size and its polarity's print, for an emotion the device does not know. */
internal fun Valence.inkSeal(): InkSeal = InkSeal(sizeOf(intensity), polarity.inkPrint)

/** The size group an intensity prints at. */
internal fun sizeOf(intensity: Int): ValenceSizeGroup =
    when (intensity) {
        1 -> ValenceSizeGroup.SMALL
        2 -> ValenceSizeGroup.MEDIUM
        else -> ValenceSizeGroup.LARGE
    }

private const val GLYPH_BOX = 200f

/** Glyph strokes keep the engine's 6 in a 200 box, at the board's 0.19 weight: 1.14 board px at any size. */
private const val GLYPH_STROKE = 6f * 0.19f
private val SOUL_MARK = listOf("M30,110 Q65,55 100,110 Q135,165 170,110")
private val GLYPH_MARK = listOf("M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101")
private val PARSER = PathParser()

private fun parse(d: String): Path = PARSER.parsePathString(d).toPath()

private val SOUL_PATHS = SOUL_MARK.map(::parse)
private val GLYPH_PATHS = GLYPH_MARK.map(::parse)

private fun DrawScope.glyphAt(
    center: Offset,
    side: Float,
    paths: List<Path>,
    unit: Float,
    colors: SpineColors,
) {
    val scale = side / GLYPH_BOX
    translate(center.x - side / 2, center.y - side / 2) {
        // The paths are in a 200 box: scale them into `side` px and keep the stroke at its on-screen weight.
        val m = Matrix().apply { scale(scale, scale) }
        paths.forEach { p ->
            val placed = Path().apply { addPath(p) }.also { it.transform(m) }
            drawPath(placed, colors.ink, style = Stroke(GLYPH_STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A first-five task's mark at [center], in board px times [unit] (the board's `FIVE_MARKS`). */
internal fun DrawScope.firstFiveMark(
    step: FirstStep,
    center: Offset,
    unit: Float,
    prints: InkPrints,
    colors: SpineColors,
) {
    when (step) {
        FirstStep.MOMENT -> sealAt(prints.of(MOMENT_SEAL, colors.ink), center, 10f * unit)
        FirstStep.PEBBLE -> sealAt(prints.of(PEBBLE_SEAL, colors.ink), center, 10f * unit)
        FirstStep.COLLECTION -> {
            val (left, top, right) = COLLECTION_SEALS
            sealAt(prints.of(left, colors.ink), center + Offset(-4.6f * unit, 2.4f * unit), 6f * unit)
            sealAt(prints.of(top, colors.ink), center + Offset(0f, -3f * unit), 6.6f * unit)
            sealAt(prints.of(right, colors.ink), center + Offset(4.6f * unit, 2.4f * unit), 6f * unit)
        }
        FirstStep.SOUL -> glyphAt(center, 14f * unit, SOUL_PATHS, unit, colors)
        FirstStep.GLYPH -> {
            drawCircle(
                colors.ink2,
                7.2f * unit,
                center,
                style = Stroke(unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f * unit, 1.6f * unit))),
            )
            glyphAt(center, 9.5f * unit, GLYPH_PATHS, unit, colors)
        }
    }
}

/** A six-firsts mark at [center]. */
internal fun DrawScope.sixFirstMark(
    kind: StoneKind,
    center: Offset,
    unit: Float,
    prints: InkPrints,
    ink: Color,
) {
    val mark = SIX_MARKS.getValue(kind)
    sealAt(prints.of(mark.seal, ink), center, mark.width * unit)
}

private val MARK_BOX = 28.dp

/**
 * A register row's mark: a first-five task, or a ghost when not yet done. A task this client does
 * not know ([step] null) keeps its row as a ghost, done or not. Decorative.
 */
@Composable
fun FirstFiveMark(
    step: FirstStep?,
    done: Boolean,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val prints = rememberInkPrints(firstFiveSeals(step.takeIf { done }).map { InkKey(it, colors.ink) }, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.7f
        if (done && step != null) firstFiveMark(step, center, unit, prints, colors) else ghost(center, 3.6f * unit, unit, colors)
    }
}

/** A register row's mark for a six-firsts kind; an unknown [kind] keeps its row as a ghost. Decorative. */
@Composable
fun SixFirstMark(
    kind: StoneKind?,
    done: Boolean,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val keys = listOfNotNull(kind?.takeIf { done }?.let { InkKey(SIX_MARKS.getValue(it).seal, colors.ink) })
    val prints = rememberInkPrints(keys, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.55f
        if (done && kind != null) sixFirstMark(kind, center, unit, prints, colors.ink) else ghost(center, 3.6f * unit, unit, colors)
    }
}

/**
 * A pebble's own seal as a register mark: its size and print, in its emotion's palette, or in ink
 * when the device does not know the emotion (spec §4). [width] in board px, in a 28 dp box.
 */
@Composable
fun PebbleSealMark(
    pebble: PebbleSnapshot,
    lookups: AchievementLookups,
    width: Float,
    modifier: Modifier = Modifier,
    box: Dp = MARK_BOX,
) {
    val colors = PebblesTheme.spine.colors
    val palette = pebble.emotionId?.let { lookups.emotion(it)?.palette }
    val emotion = rememberEmotionSeals(listOfNotNull(palette?.let { SealKey(pebble.valence, it) }), colors.stock, SealVariant.MINI)
    val inkSeal = pebble.valence.inkSeal()
    val ink = rememberInkPrints(if (palette == null) listOf(InkKey(inkSeal, colors.ink)) else emptyList(), colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        val print = palette?.let { emotion.getValue(SealKey(pebble.valence, it)) } ?: ink.of(inkSeal, colors.ink)
        sealAt(print, center, width * unit)
    }
}
