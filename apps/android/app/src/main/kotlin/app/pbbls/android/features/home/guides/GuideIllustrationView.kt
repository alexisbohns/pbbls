package app.pbbls.android.features.home.guides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.DotMark
import app.pbbls.android.core.designsystem.DotStyle
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.home.GuideIllustration
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.core.ui.render.stone.drawSeal
import app.pbbls.android.core.ui.render.stone.inkPrint
import app.pbbls.android.core.ui.render.stone.rememberInkSealPrint

/** The band's height under the summary (iOS `GuideIllustrationView.pageHeight`). */
private val PAGE_HEIGHT = 120.dp

private val ARROW = 14.dp
private val DOMAIN_DOT = 12.dp
private val SOUL_SIDE = 28.dp
private val GLYPH_RING = 56.dp
private val GLYPH_SIDE = 36.dp
private val GLYPH_RING_STROKE = 1.dp
private val GLYPH_RING_DASH = 4.dp

/** The glyphs' square, as the checklist's sample marks are drawn (iOS `ChecklistMarks`). */
private const val MARK_VIEW_BOX = "0 0 200 200"

/**
 * Three sample marks (iOS `ChecklistMarks.souls`): a wave, a spiral with its dot, a ridge. Copied here rather than
 * shared, so Home's guides and its checklist each own their pictures.
 */
private val SAMPLE_MARKS: List<List<GlyphStroke>> =
    listOf(
        listOf(GlyphStroke("M30,110 Q65,55 100,110 Q135,165 170,110", 6.0)),
        listOf(
            GlyphStroke("M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", 6.0),
            GlyphStroke("M100,100 L100,101", 6.0),
        ),
        listOf(GlyphStroke("M30,160 L80,70 L115,125 L140,90 L175,160", 6.0)),
    )

/** The nine stones by row (polarity) and column (size), for the stones' grid. */
private val GRID: List<List<Valence>> =
    listOf(
        listOf(Valence.LOWLIGHT_SMALL, Valence.LOWLIGHT_MEDIUM, Valence.LOWLIGHT_LARGE),
        listOf(Valence.NEUTRAL_SMALL, Valence.NEUTRAL_MEDIUM, Valence.NEUTRAL_LARGE),
        listOf(Valence.HIGHLIGHT_SMALL, Valence.HIGHLIGHT_MEDIUM, Valence.HIGHLIGHT_LARGE),
    )

private val VALENCES = listOf(Valence.LOWLIGHT_MEDIUM, Valence.NEUTRAL_MEDIUM, Valence.HIGHLIGHT_MEDIUM)

private const val FAMILY_COUNT = 7

/**
 * A guide's picture on its page (#1383 D13; iOS `GuideIllustrationView`, page size): a 120 dp band drawn with the
 * real parts the reading is about, seals, soul marks, a glyph, day dots. No colour but what those parts carry.
 * The emotions' seals wear one family palette each ([familyPalettes], in the families' order), the brand's ink for
 * a family missing. Decorative: the title above says what it is.
 */
@Composable
internal fun GuideIllustrationView(
    kind: GuideIllustration,
    modifier: Modifier = Modifier,
    familyPalettes: List<EmotionPalette> = emptyList(),
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PAGE_HEIGHT)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        GuidePicture(kind, familyPalettes)
    }
}

@Composable
private fun GuidePicture(
    kind: GuideIllustration,
    familyPalettes: List<EmotionPalette>,
) {
    val spacing = PebblesTheme.spacing
    val colors = MaterialTheme.colorScheme
    when (kind) {
        GuideIllustration.PEBBLES ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                BlankSeal(Valence.NEUTRAL_MEDIUM)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = colors.outline,
                    modifier = Modifier.size(ARROW),
                )
                InkSeal(Valence.HIGHLIGHT_MEDIUM)
            }

        GuideIllustration.STONES ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                GRID.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
                        row.forEach { InkSeal(it) }
                    }
                }
            }

        GuideIllustration.EMOTIONS ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                repeat(FAMILY_COUNT) { index ->
                    val palette = familyPalettes.getOrNull(index)
                    if (palette != null) {
                        StoneSeal(
                            valence = Valence.HIGHLIGHT_MEDIUM,
                            palette = palette,
                            glyph = null,
                            knockout = PebblesTheme.spine.colors.ground,
                            size = SealSize.CONTENTS,
                        )
                    } else {
                        InkSeal(Valence.HIGHLIGHT_MEDIUM)
                    }
                }
            }

        GuideIllustration.VALENCE ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
                VALENCES.forEach { InkSeal(it) }
            }

        GuideIllustration.DOMAINS ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                listOf(DotStyle.FILLED, DotStyle.RING, DotStyle.QUIET).forEach { style ->
                    DotMark(style = style, color = colors.onSurfaceVariant, diameter = DOMAIN_DOT)
                }
            }

        // iOS draws a sample collection's row here; Android has no shared row in core, so the stack it holds.
        GuideIllustration.COLLECTIONS ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                InkSeal(Valence.HIGHLIGHT_SMALL)
                InkSeal(Valence.NEUTRAL_SMALL)
                InkSeal(Valence.LOWLIGHT_SMALL)
            }

        GuideIllustration.SOULS ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                SAMPLE_MARKS.forEachIndexed { index, strokes ->
                    SoulMark(
                        glyph = Glyph(id = "guide-soul-$index", strokes = strokes, viewBox = MARK_VIEW_BOX),
                        ink = colors.onSurfaceVariant,
                        modifier = Modifier.size(SOUL_SIDE),
                    )
                }
            }

        GuideIllustration.GLYPHS ->
            Box(Modifier.size(GLYPH_RING), contentAlignment = Alignment.Center) {
                val ring = colors.outline
                Canvas(Modifier.size(GLYPH_RING)) {
                    val stroke = GLYPH_RING_STROKE.toPx()
                    val dash = GLYPH_RING_DASH.toPx()
                    drawCircle(
                        color = ring,
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                }
                GlyphImage(
                    strokes = SAMPLE_MARKS[1],
                    viewBox = MARK_VIEW_BOX,
                    strokeColor = colors.onSurfaceVariant,
                    modifier = Modifier.size(GLYPH_SIDE),
                )
            }

        GuideIllustration.KARMA ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                InkSeal(Valence.HIGHLIGHT_MEDIUM)
                Text(text = "+1", style = PebblesTheme.spine.type.kicker, color = colors.onSurfaceVariant)
            }
    }
}

/** A lit seal in the brand's ink, as iOS's `GuideSeals.lit` without a palette: the contents row's size. */
@Composable
private fun InkSeal(valence: Valence) {
    val size = SealSize.CONTENTS
    val print =
        rememberInkSealPrint(
            size = valence.sizeGroup,
            print = valence.polarity.inkPrint,
            ink = MaterialTheme.colorScheme.primary,
            knockout = PebblesTheme.spine.colors.ground,
            variant = size.variant,
        )
    Canvas(Modifier.width(size.width(valence.sizeGroup)).aspectRatio(print.aspectRatio)) { drawSeal(print, this.size.width) }
}

/** A moment with no emotion yet: the blank, uncarved seal (iOS `GuideSeals.blank`). */
@Composable
private fun BlankSeal(valence: Valence) {
    StoneSeal(valence = valence, palette = null, glyph = null, knockout = PebblesTheme.spine.colors.ground, size = SealSize.CONTENTS)
}
