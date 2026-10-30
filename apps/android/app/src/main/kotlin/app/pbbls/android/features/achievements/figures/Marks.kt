package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.PebbleSnapshot
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.core.ui.render.stone.InkPrint
import app.pbbls.android.core.ui.render.stone.SealVariant
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.features.achievements.AchievementLookups

/** One six-firsts mark: a size, a print and a width in board px. */
data class SixMark(
    val size: ValenceSizeGroup,
    val print: InkPrint,
    val width: Float,
)

/**
 * The board's six-firsts table. The board prints the three intensities on the neutral outline in
 * a solid print; Android's outline follows its print, so they take the highlight outline, the solid
 * print's own (a few dp of silhouette apart).
 */
val SIX_MARKS: Map<StoneKind, SixMark> =
    mapOf(
        StoneKind.SMALL to SixMark(ValenceSizeGroup.SMALL, InkPrint.RELIEF, 9f),
        StoneKind.MEDIUM to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12f),
        StoneKind.LARGE to SixMark(ValenceSizeGroup.LARGE, InkPrint.RELIEF, 14f),
        StoneKind.LOWLIGHT to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.ENGRAVED, 12.5f),
        StoneKind.NEUTRAL to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.OPEN, 12.5f),
        StoneKind.HIGHLIGHT to SixMark(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF, 12.5f),
    )

/** An emotion's seal width on the wheel, in board px (`7.4 + intensity × 1.6`). */
fun emotionSealWidth(intensity: Int): Float = 7.4f + intensity * 1.6f

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
    seals: InkSeals,
    colors: SpineColors,
) {
    when (step) {
        FirstStep.MOMENT -> sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.BLANK), center, 10f * unit)
        FirstStep.PEBBLE -> sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.RELIEF), center, 10f * unit)
        FirstStep.COLLECTION -> {
            sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.RELIEF), center + Offset(-4.6f * unit, 2.4f * unit), 6f * unit)
            sealAt(seals.of(ValenceSizeGroup.MEDIUM, InkPrint.OPEN), center + Offset(0f, -3f * unit), 6.6f * unit)
            sealAt(seals.of(ValenceSizeGroup.SMALL, InkPrint.ENGRAVED), center + Offset(4.6f * unit, 2.4f * unit), 6f * unit)
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
    seals: InkSeals,
) {
    val mark = SIX_MARKS.getValue(kind)
    sealAt(seals.of(mark.size, mark.print), center, mark.width * unit)
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
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.7f
        if (done && step != null) firstFiveMark(step, center, unit, seals, colors) else ghost(center, 3.6f * unit, unit, colors)
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
    val seals = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f * 1.55f
        if (done && kind != null) sixFirstMark(kind, center, unit, seals) else ghost(center, 3.6f * unit, unit, colors)
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
    val ink = rememberInkSeals(colors.ink, colors.stock, SealVariant.MINI)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        val print =
            palette?.let { emotion.getValue(SealKey(pebble.valence, it)) }
                ?: ink.of(sizeOf(pebble.valence.intensity), pebble.valence.polarity.inkPrint)
        sealAt(print, center, width * unit)
    }
}
