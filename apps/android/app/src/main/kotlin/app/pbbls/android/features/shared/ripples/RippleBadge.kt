package app.pbbls.android.features.shared.ripples

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * 44×44 ring-and-digit badge representing the user's Ripples level — ports
 * iOS `RippleBadge.swift`. See `docs/superpowers/specs/2026-05-15-ripples-design.md`
 * and issue #442 for the full color/state semantics. Outermost rings draw
 * first so inner rings paint on top; ring opacity steps 0.33 / 0.66 / 1.0
 * from outside in.
 */
@Composable
fun RippleBadge(
    level: Int,
    activeToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val clampedLevel = level.coerceIn(0, 6)
    // Resolve the palette bundle in composition; the tone→color mapping is a
    // pure function so the DrawScope lambda can call it.
    val colors = PebblesTheme.colors
    val digitColor = colors.system.foreground
    val label =
        if (activeToday) {
            stringResource(R.string.ripple_badge_active, clampedLevel)
        } else {
            stringResource(R.string.ripple_badge_inactive, clampedLevel)
        }

    Box(
        modifier =
            modifier
                .size(44.dp)
                .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            // Uniform scale from the 44-unit source viewBox into the canvas.
            scale(
                scaleX = size.width / RIPPLE_VIEWBOX_SIDE,
                scaleY = size.height / RIPPLE_VIEWBOX_SIDE,
                pivot = Offset.Zero,
            ) {
                listOf(
                    6 to 0.33f,
                    5 to 0.33f,
                    4 to 0.33f,
                    3 to 0.66f,
                    2 to 0.66f,
                    1 to 1f,
                ).forEach { (id, alpha) ->
                    val tone = rippleStrokeTone(strokeId = id, level = clampedLevel, activeToday = activeToday)
                    drawPath(
                        path = RippleStrokes.byId.getValue(id),
                        color = tone.color(colors),
                        alpha = alpha,
                        style = stroke,
                    )
                }
            }
        }
        // The Box's clearAndSetSemantics carries the combined a11y label.
        PebblesText(
            text = clampedLevel.toString(),
            style = PebblesTypography.captionEmphasized,
            color = digitColor,
        )
    }
}
