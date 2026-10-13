package app.pbbls.android.core.ui.render

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.ui.render.wobble.WobbleFlags
import app.pbbls.android.core.ui.render.wobble.WobbleRenderer
import app.pbbls.android.core.ui.render.wobble.wobbleInkPath

/**
 * Traces a composed stone ([StoneComposer]: shape seed + derived glyph) by
 * drawing every layer at a single stroke weight ([PebbleStroke.OUTLINE_WIDTH])
 * instead of honoring each layer's authored width — the Android analog of iOS
 * `PebbleStaticRenderView` (PR #511).
 *
 * The outline is authored at that weight already, so it looks unchanged; the
 * glyph reads at the outline's weight too, so collection and domain glyphs
 * render identically on the Path and the read page.
 *
 * Wobble experiment (#555, debug-only via [WobbleFlags]): instead of stroking,
 * fills the wobbled ink — thickness is baked into the leaky outline. The art is
 * keyed by [cacheKey], both here (`remember(cacheKey, model)` re-derives on
 * in-place row updates) and in [WobbleRenderer]'s cross-composable cache.
 *
 * A model whose path data can't be traced, or an unreadable [strokeHex], logs
 * and draws nothing. Used by [PebbleStone] and [BlankStone].
 */
@Composable
internal fun PebbleStaticRender(
    model: PebbleSvgModel,
    cacheKey: String,
    strokeHex: String,
    modifier: Modifier = Modifier,
) {
    val renderLayers = remember(model) { buildRenderLayers(model) }
    val strokeColor = remember(strokeHex) { EmotionPalette.parseColor(strokeHex) }
    val wobbleLayers =
        remember(cacheKey, model) {
            if (WobbleFlags.isEnabled && renderLayers != null) {
                buildWobbleLayers(cacheKey, model)
            } else {
                null
            }
        }

    if (renderLayers == null || strokeColor == null) {
        LaunchedEffect(cacheKey, strokeHex) {
            Log.e(TAG, "stone $cacheKey not drawn: untraceable path data or unreadable stroke $strokeHex")
        }
        return
    }

    val viewBox = model.viewBox
    Canvas(modifier = modifier) {
        if (viewBox.width <= 0f || viewBox.height <= 0f) return@Canvas
        val fit = minOf(size.width / viewBox.width, size.height / viewBox.height)
        if (fit <= 0f) return@Canvas
        // Center the fit-scaled viewBox in the frame (the `.fit` contract shared
        // with SvgCanvas). The stroke width rides the same `fit` scale, so every
        // layer draws at OUTLINE_WIDTH × fit on screen.
        val dx = (size.width - viewBox.width * fit) / 2f - viewBox.minX * fit
        val dy = (size.height - viewBox.height * fit) / 2f - viewBox.minY * fit
        withTransform({
            translate(dx, dy)
            scale(fit, fit, pivot = Offset.Zero)
        }) {
            if (wobbleLayers != null) {
                wobbleLayers.forEach { layer ->
                    drawPath(
                        path = layer.path,
                        color = strokeColor,
                        alpha = layer.opacity,
                        style = Fill,
                    )
                }
            } else {
                renderLayers.forEach { layer ->
                    drawPath(
                        path = layer.path,
                        color = strokeColor,
                        alpha = layer.opacity,
                        style =
                            Stroke(
                                width = PebbleStroke.OUTLINE_WIDTH,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )
                }
            }
        }
    }
}

private const val TAG = "PebbleStaticRender"

/** A layer flattened to a single viewBox-space Compose [Path] plus its opacity. */
private class RenderLayer(
    val opacity: Float,
    val path: Path,
)

/**
 * Builds the per-layer Compose paths from a parsed [PebbleSvgModel], baking the
 * layer's own transform and each path's inner `translate`/`scale` transform
 * into viewBox-space geometry. Returns `null` if any path fails to parse, so
 * the caller draws nothing.
 */
private fun buildRenderLayers(model: PebbleSvgModel): List<RenderLayer>? =
    try {
        model.layers.map { layer ->
            val combined = Path()
            for (spec in layer.paths) {
                val parsed = PathParser().parsePathString(spec.d).toPath()
                parsed.transform(layer.transform.concat(spec.transform).toMatrix())
                combined.addPath(parsed)
            }
            RenderLayer(opacity = layer.opacity, path = combined)
        }
    } catch (e: Exception) {
        // A malformed `d` is a data condition, not a crash — draw nothing.
        Log.w(TAG, "stone path data not traceable", e)
        null
    }

/**
 * Per-layer wobbled ink as viewBox-space Compose paths (each glyph layer's ink
 * is built in its 200-slot space, so the layer transform is baked in here —
 * the `WobbledPathShape` composition on iOS).
 */
private fun buildWobbleLayers(
    cacheKey: String,
    model: PebbleSvgModel,
): List<RenderLayer> {
    val art = WobbleRenderer.pebbleArt(cacheKey, model)
    return model.layers.mapIndexed { index, layer ->
        RenderLayer(
            opacity = layer.opacity,
            path = wobbleInkPath(art.layers[index].ink, layer.transform),
        )
    }
}

/**
 * Converts an [Affine] (translate + scale only, no shear/rotation) to a Compose
 * [Matrix]. `translate` then `scale` composes to `p → (a·x + e, d·y + f)`.
 */
private fun Affine.toMatrix(): Matrix =
    Matrix().apply {
        translate(x = e, y = f)
        scale(x = a, y = d)
    }
