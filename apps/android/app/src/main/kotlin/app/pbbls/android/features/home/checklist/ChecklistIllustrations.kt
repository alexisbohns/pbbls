package app.pbbls.android.features.home.checklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.CalendarLabels
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.core.ui.render.stone.rememberInkSealPrint
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The picture's height (iOS `ChecklistIllustration.height`). */
val ChecklistIllustrationHeight: Dp = 120.dp

// The contents row, as the week's contents draw it (`SpineLayout`'s numbers, which Home cannot import).
private val CONTENTS_ROW_MIN = 42.dp
private val CONTENTS_DAY_COL = 40.dp
private val CONTENTS_SEAL_COL = 36.dp
private val CONTENTS_SEAL_END = 6.dp
private val CONTENTS_NAME_END = 10.dp
private val CONTENTS_SOUL = 16.dp
private val CONTENTS_SOUL_GAP = 6.dp
private const val CONTENTS_NAME_WEIGHT = 450
private val RULE = 1.dp

private val ARROW = 14.dp
private val ARROW_GAP = 6.dp
private val SOUL_SIDE = 28.dp
private val GLYPH_RING = 56.dp
private val GLYPH_SIDE = 36.dp
private val RING_STROKE = 1.dp
private val RING_DASH = 4.dp

/** iOS's sample time: Tuesday 27 October 2026, 12:40. */
private val SAMPLE: LocalDateTime = LocalDateTime.of(2026, 10, 27, 12, 40)

private const val GLYPH_BOX = "0 0 200 200"

/**
 * Three small hand-drawn marks in glyph space (200 × 200, 6 pt strokes), so the pictures need no
 * fixture: a wave, a loop, a ridge (iOS `ChecklistMarks.souls`, verbatim).
 */
private val checklistMarks: List<Glyph> =
    listOf(
        listOf(GlyphStroke("M30,110 Q65,55 100,110 Q135,165 170,110", 6.0)),
        listOf(
            GlyphStroke("M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", 6.0),
            GlyphStroke("M100,100 L100,101", 6.0),
        ),
        listOf(GlyphStroke("M30,160 L80,70 L115,125 L140,90 L175,160", 6.0)),
    ).mapIndexed { k, strokes -> Glyph(id = "checklist-mark-$k", strokes = strokes, viewBox = GLYPH_BOX) }

/**
 * A task sheet's picture (#1383 D11; iOS `ChecklistIllustration`), drawn with the real parts the task
 * makes: the contents row and its seals, a collection, soul marks, a glyph. Decorative inside; the
 * whole reads one short label. [knockout] is the ground the seals are cut back to (the sheet's).
 */
@Composable
fun ChecklistIllustration(
    task: ChecklistTask,
    knockout: Color,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(task.illustrationRes)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChecklistIllustrationHeight)
                .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (task) {
            ChecklistTask.MOMENT -> ContentsLine { BlankSeal(knockout) }
            ChecklistTask.PEBBLE ->
                ContentsLine {
                    Row(horizontalArrangement = Arrangement.spacedBy(ARROW_GAP), verticalAlignment = Alignment.CenterVertically) {
                        BlankSeal(knockout)
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(ARROW),
                        )
                        LitSeal(Valence.HIGHLIGHT_MEDIUM, knockout)
                    }
                }
            ChecklistTask.COLLECTION -> CollectionPicture(knockout)
            ChecklistTask.SOUL ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        checklistMarks.forEach { SoulMark(it, PebblesTheme.spine.colors.ink, Modifier.size(SOUL_SIDE)) }
                    }
                    ContentsLine(soul = checklistMarks[0]) { LitSeal(Valence.NEUTRAL_MEDIUM, knockout) }
                }
            ChecklistTask.GLYPH -> GlyphPicture()
        }
    }
}

@Composable
private fun CollectionPicture(knockout: Color) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
            LitSeal(Valence.HIGHLIGHT_SMALL, knockout)
            LitSeal(Valence.NEUTRAL_LARGE, knockout)
            LitSeal(Valence.LOWLIGHT_MEDIUM, knockout)
        }
        // The collections list's row: the name over its count.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.home_task_sample_collection),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Text(
                text = pluralStringResource(R.plurals.pebbles_count, SAMPLE_COUNT, SAMPLE_COUNT),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private const val SAMPLE_COUNT = 3

@Composable
private fun GlyphPicture() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier.size(GLYPH_RING).drawBehind {
                val stroke = RING_STROKE.toPx()
                drawCircle(
                    color = colors.outline,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(RING_DASH.toPx(), RING_DASH.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphImage(
            strokes = checklistMarks[1].strokes,
            viewBox = GLYPH_BOX,
            strokeColor = colors.onSurfaceVariant,
            modifier = Modifier.size(GLYPH_SIDE),
        )
    }
}

/**
 * One contents row as the week's contents draw it (`WeekContents`): the day in small caps, the seal,
 * the sample name, the time, between two rules. It mirrors that row; edit the two together.
 */
@Composable
private fun ContentsLine(
    soul: Glyph? = null,
    seal: @Composable () -> Unit,
) {
    val spine = PebblesTheme.spine
    val locale = LocalConfiguration.current.locales[0]
    val weekday = remember(locale) { CalendarLabels.shortWeekday(SAMPLE.dayOfWeek, locale) }
    val time = remember(locale) { SAMPLE.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CONTENTS_ROW_MIN)
                .drawBehind {
                    val rule = RULE.toPx()
                    drawLine(spine.colors.rule, Offset(0f, rule / 2), Offset(size.width, rule / 2), rule)
                    drawLine(spine.colors.rule, Offset(0f, size.height - rule / 2), Offset(size.width, size.height - rule / 2), rule)
                }.padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = weekday,
            style = spine.type.contentsDay,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(CONTENTS_DAY_COL),
        )
        Box(Modifier.padding(end = CONTENTS_SEAL_END).width(CONTENTS_SEAL_COL), contentAlignment = Alignment.CenterStart) { seal() }
        Row(
            modifier = Modifier.weight(1f).padding(end = CONTENTS_NAME_END),
            horizontalArrangement = Arrangement.spacedBy(CONTENTS_SOUL_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            soul?.let { SoulMark(it, spine.colors.ink, Modifier.size(CONTENTS_SOUL)) }
            Text(
                text = stringResource(R.string.home_task_sample_moment),
                style = spine.type.contentsName(CONTENTS_NAME_WEIGHT, italic = false),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = time, style = spine.type.contentsTime, color = MaterialTheme.colorScheme.outline)
    }
}

/** A moment with no pebble yet: the blank river stone. */
@Composable
private fun BlankSeal(knockout: Color) {
    StoneSeal(valence = Valence.NEUTRAL_MEDIUM, palette = null, glyph = null, knockout = knockout, size = SealSize.CONTENTS)
}

/**
 * A pebble with no emotion to colour it: printed in the brand's spot ink, `primary` on Paper and
 * `secondary` at Night, as a seal prints (iOS `SpineColors.palette(nil, isBlank: false)` is the brand's).
 */
@Composable
private fun LitSeal(
    valence: Valence,
    knockout: Color,
) {
    val colors = MaterialTheme.colorScheme
    val ink = if (PebblesTheme.spine.colors.isNight) colors.secondary else colors.primary
    val print = rememberInkSealPrint(valence.sizeGroup, valence.polarity.inkPrint, ink, knockout)
    Canvas(Modifier.width(SealSize.CONTENTS.width(valence.sizeGroup)).aspectRatio(print.aspectRatio)) { drawSeal(print, size.width) }
}
