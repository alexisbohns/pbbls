package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import kotlin.math.min
import app.pbbls.android.features.achievements.figures.SkylineGeometry as G

/** A skyline path placed: board units → px, `p' = (p − origin) × scale`. */
private fun placed(
    d: String,
    scale: Float,
    origin: Offset,
): Path =
    PathParser().parsePathString(d).toPath().also { p ->
        p.translate(-origin)
        p.transform(Matrix().apply { scale(scale, scale) })
    }

/**
 * The skyline (g/92): [fill]'s stages in ink, the next rising from its foot, the contour over all
 * of them in secondary ink until every stage stands. [viewport] is the board's view box; the
 * height follows it. As wide as the room allows, up to [width]. One node: [description].
 */
@Composable
fun Skyline(
    fill: SkylineFill,
    description: String,
    modifier: Modifier = Modifier,
    viewport: G.Viewport = G.BIG,
    width: Dp = 300.dp,
) {
    val colors = PebblesTheme.spine.colors
    Canvas(
        modifier
            .fillMaxWidth()
            .widthIn(max = width)
            .aspectRatio(viewport.width / viewport.height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val scale = size.width / viewport.width
        val origin = Offset(viewport.left, viewport.top)
        for (k in 0 until fill.full.coerceAtMost(fill.stages)) drawPath(placed(G.STAGES[k], scale, origin), colors.ink)
        if (fill.full < fill.stages && fill.partial > 0f) {
            val r = G.partialClip(fill.full, fill.partial)
            clipRect(
                left = (r.left - origin.x) * scale,
                top = (r.top - origin.y) * scale,
                right = (r.right - origin.x) * scale,
                bottom = (r.bottom - origin.y) * scale,
            ) { drawPath(placed(G.STAGES[fill.full], scale, origin), colors.ink) }
        }
        drawPath(
            placed(G.contour(fill.stages, viewport.end), scale, origin),
            if (fill.done) colors.ink else colors.ink2,
            style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val STAGE_W_PAD = 4f
private const val STAGE_H_PAD = 2f
private const val STAGE_BOX_W = 28f
private const val STAGE_BOX_H = 26f
private const val BASELINE_OVERHANG = 3f

/**
 * One stage alone, as a register row's mark (the board's `stage`): solid when [fill] is 1,
 * otherwise its contour with the fill it has, on its own ground line. Stages past the eighth
 * reuse the last. Decorative.
 */
@Composable
fun SkylineStage(
    stage: Int,
    fill: Float,
    modifier: Modifier = Modifier,
    box: Dp = 28.dp,
    scale: Float = 1f,
) {
    val colors = PebblesTheme.spine.colors
    val k = stage.coerceIn(0, G.STAGES.lastIndex)
    Canvas(modifier.size(box).clearAndSetSemantics {}) {
        val (x0, x1) = G.STAGE_X[k]
        val top = G.STAGE_TOP[k]
        val w = x1 - x0 + STAGE_W_PAD
        val h = G.GROUND - top + STAGE_H_PAD
        val unit = size.width / STAGE_BOX_W
        val s = min(STAGE_BOX_W / w, STAGE_BOX_H / h) * scale * unit
        val origin = Offset((x0 + x1) / 2 - center.x / s, (G.GROUND + top) / 2 - center.y / s)
        if (fill >= 1f) {
            drawPath(placed(G.STAGES[k], s, origin), colors.ink)
        } else {
            if (fill > 0f) {
                val r = G.partialClip(k, fill)
                clipRect(
                    left = (r.left - origin.x) * s,
                    top = (r.top - origin.y) * s,
                    right = (r.right - origin.x) * s,
                    bottom = (r.bottom - origin.y) * s,
                ) { drawPath(placed(G.STAGES[k], s, origin), colors.ink) }
            }
            drawPath(placed(G.STAGES[k], s, origin), colors.ink2, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        }
        val y = (G.GROUND - origin.y) * s
        drawLine(
            colors.ink2,
            Offset((x0 - BASELINE_OVERHANG - origin.x) * s, y),
            Offset((x1 + BASELINE_OVERHANG - origin.x) * s, y),
            1.dp.toPx(),
        )
    }
}
