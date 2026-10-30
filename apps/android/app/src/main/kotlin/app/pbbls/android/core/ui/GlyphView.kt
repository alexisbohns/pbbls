package app.pbbls.android.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.ui.render.GlyphImage

/**
 * Which chrome + tint a glyph slot renders — ports iOS `GlyphView.Case`
 * (visual spec table: `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §2).
 * Only the carving/empty states ([CARVE], [CREATE]) draw the dashed
 * `shapes.extraLarge` frame; glyph-bearing states render chromeless
 * (issue #515) — selection is carried by glyph color, not a frame.
 */
enum class GlyphViewCase {
    PROFILE, // no frame; glyph in primary
    CARVE, // dashed 2dp outlineVariant; the dotted-glyph mark in onSurfaceVariant
    CREATE, // dashed 2dp outlineVariant; the plus stone in outline
    SELECTED, // no frame; glyph in primary
    UNSELECTED, // no frame; glyph in outlineVariant — faint, but still tappable, so not the 38% disabled ink
    DEFAULT, // no frame; glyph in onSurfaceVariant
}

/**
 * Canonical glyph chrome component — ports iOS
 * `Features/Glyph/Views/GlyphView.swift`. Renders either the glyph [strokes]
 * (through the existing [GlyphImage] pipeline) or a placeholder icon overlay
 * (`pebble-dotted-glyph` for [GlyphViewCase.CARVE], `plus-circle` for [GlyphViewCase.CREATE]).
 * Named `GlyphView` for iOS symmetry; the model type owns `Glyph`.
 */
@Composable
fun GlyphView(
    case: GlyphViewCase,
    modifier: Modifier = Modifier,
    strokes: List<GlyphStroke>? = null,
    viewBox: String = "0 0 200 200",
    side: Dp = 96.dp,
) {
    val colors = MaterialTheme.colorScheme
    val dashed = case == GlyphViewCase.CARVE || case == GlyphViewCase.CREATE
    val frameColor = colors.outlineVariant
    // A drawn round-rect needs a radius, not a Shape: resolve the role's corner
    // against the slot size inside the draw scope (#853, was Spacing.xxl).
    val corner = MaterialTheme.shapes.extraLarge.topStart

    Box(
        modifier =
            modifier
                .size(side)
                .drawBehind {
                    if (dashed) {
                        val strokeWidth = 2.dp.toPx()
                        val dash = 10.dp.toPx()
                        val inset = strokeWidth / 2f
                        drawRoundRect(
                            color = frameColor,
                            topLeft = Offset(inset, inset),
                            size =
                                Size(
                                    size.width - strokeWidth,
                                    size.height - strokeWidth,
                                ),
                            cornerRadius = CornerRadius(corner.toPx(size, this) - inset),
                            style =
                                Stroke(
                                    width = strokeWidth,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                                ),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        when (case) {
            GlyphViewCase.CARVE ->
                PlaceholderIcon(PbblsIcon.PEBBLE_DOTTED_GLYPH, side, tint = colors.onSurfaceVariant)
            GlyphViewCase.CREATE ->
                PlaceholderIcon(PbblsIcon.PLUS_CIRCLE, side, tint = colors.outline)
            GlyphViewCase.PROFILE, GlyphViewCase.SELECTED ->
                GlyphStrokes(strokes, viewBox, colors.primary)
            GlyphViewCase.UNSELECTED ->
                GlyphStrokes(strokes, viewBox, colors.outlineVariant)
            GlyphViewCase.DEFAULT ->
                GlyphStrokes(strokes, viewBox, colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceholderIcon(
    icon: PbblsIcon,
    side: Dp,
    tint: Color,
) {
    // A fixed slot (spec D6): the cell is a dp square that does not follow the
    // font scale, so the mark takes iOS's max(side * 0.4, 18) as a Dp.
    val iconSide = if (side * 0.4f > 18.dp) side * 0.4f else 18.dp
    PbblsIconImage(icon = icon, contentDescription = null, size = iconSide, tint = tint)
}

@Composable
private fun GlyphStrokes(
    strokes: List<GlyphStroke>?,
    viewBox: String,
    color: Color,
) {
    GlyphImage(
        strokes = strokes.orEmpty(),
        viewBox = viewBox,
        strokeColor = color,
        modifier = Modifier.fillMaxSize(),
    )
}
