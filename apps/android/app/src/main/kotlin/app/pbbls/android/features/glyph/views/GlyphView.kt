package app.pbbls.android.features.glyph.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.render.GlyphImage
import app.pbbls.android.theme.PebblesTheme

/**
 * Which chrome + tint a glyph slot renders — ports iOS `GlyphView.Case`
 * (visual spec table: `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §2).
 * Only the carving/empty states ([CARVE], [CREATE]) draw the dashed
 * `Spacing.xxl`-radius frame; glyph-bearing states render chromeless
 * (issue #515) — selection is carried by glyph color, not a frame.
 */
enum class GlyphViewCase {
    PROFILE, // no frame; glyph in accent.primary
    CARVE, // dashed 2dp system.muted; scribble in system.secondary
    CREATE, // dashed 2dp system.muted; plus in system.muted
    SELECTED, // no frame; glyph in accent.primary
    UNSELECTED, // no frame; glyph in system.muted
    DEFAULT, // no frame; glyph in system.secondary
}

/**
 * Canonical glyph chrome component — ports iOS
 * `Features/Glyph/Views/GlyphView.swift`. Renders either the glyph [strokes]
 * (through the existing [GlyphImage] pipeline) or a placeholder icon overlay
 * (scribble for [GlyphViewCase.CARVE], plus for [GlyphViewCase.CREATE]).
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
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val dashed = case == GlyphViewCase.CARVE || case == GlyphViewCase.CREATE
    val frameColor = system.muted
    val cornerRadius = PebblesTheme.spacing.xxl

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
                            cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
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
                PlaceholderIcon(R.drawable.ic_scribble, side, tint = system.secondary)
            GlyphViewCase.CREATE ->
                PlaceholderIcon(R.drawable.ic_plus, side, tint = system.muted)
            GlyphViewCase.PROFILE, GlyphViewCase.SELECTED ->
                GlyphStrokes(strokes, viewBox, accent.primary)
            GlyphViewCase.UNSELECTED ->
                GlyphStrokes(strokes, viewBox, system.muted)
            GlyphViewCase.DEFAULT ->
                GlyphStrokes(strokes, viewBox, system.secondary)
        }
    }
}

@Composable
private fun PlaceholderIcon(
    resId: Int,
    side: Dp,
    tint: Color,
) {
    // iOS sizes the SF Symbol at max(side * 0.4, 18pt).
    val iconSide = if (side * 0.4f > 18.dp) side * 0.4f else 18.dp
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(iconSide),
    )
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
