package app.pbbls.android.features.path.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.render.wobble.WobbleFlags
import app.pbbls.android.features.path.render.wobble.WobbleRenderer
import app.pbbls.android.features.path.render.wobble.wobbleInkPath
import java.util.Locale

/**
 * Renders a carved glyph — the iOS `GlyphView` analog (D13, pulled forward into
 * sub-project B so the read view's soul cells can render glyphs before the
 * create flow ships). Builds a minimal SVG from [strokes] and feeds it through
 * the existing [PebbleSvg] pipeline: strokes are authored as
 * `stroke="currentColor"`, which [PebbleSvg] substitutes with [strokeColor]'s
 * hex before AndroidSVG parses the markup. Reused by C's glyph picker and D's
 * form row.
 *
 * Wobble experiment (#555, debug-only via [WobbleFlags]): each committed carve
 * stroke renders as wobbled filled ink instead (the iOS `GlyphThumbnail`
 * seam), falling back per-stroke to the plain rounded stroke when a `d`
 * doesn't parse — and to the [PebbleSvg] pipeline entirely when the viewBox
 * doesn't.
 */
@Composable
fun GlyphImage(
    strokes: List<GlyphStroke>,
    viewBox: String,
    strokeColor: Color,
    modifier: Modifier = Modifier,
) {
    val wobbleStrokes =
        remember(strokes) {
            if (WobbleFlags.isEnabled) strokes.map(::buildWobbleStroke) else null
        }
    val box = remember(viewBox) { parseGlyphViewBox(viewBox) }

    if (wobbleStrokes != null && box != null) {
        Canvas(modifier = modifier) {
            if (box.width <= 0f || box.height <= 0f) return@Canvas
            // Same aspect-fit + centering contract as SvgCanvas.
            val fit = minOf(size.width / box.width, size.height / box.height)
            if (fit <= 0f) return@Canvas
            val dx = (size.width - box.width * fit) / 2f - box.minX * fit
            val dy = (size.height - box.height * fit) / 2f - box.minY * fit
            withTransform({
                translate(dx, dy)
                scale(fit, fit, pivot = Offset.Zero)
            }) {
                wobbleStrokes.forEach { stroke ->
                    when (stroke) {
                        is WobbleStrokeRender.Ink ->
                            drawPath(path = stroke.path, color = strokeColor, style = Fill)
                        is WobbleStrokeRender.Plain ->
                            drawPath(
                                path = stroke.path,
                                color = strokeColor,
                                style =
                                    Stroke(
                                        width = stroke.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                            )
                    }
                }
            }
        }
        return
    }

    val markup = remember(strokes, viewBox) { buildGlyphSvg(strokes, viewBox) }
    PebbleSvg(svg = markup, strokeHex = rgbHex(strokeColor), modifier = modifier)
}

/** One stroke's render mode: wobbled filled ink, or the plain rounded stroke. */
private sealed interface WobbleStrokeRender {
    class Ink(
        val path: Path,
    ) : WobbleStrokeRender

    class Plain(
        val path: Path,
        val width: Float,
    ) : WobbleStrokeRender
}

private fun buildWobbleStroke(stroke: GlyphStroke): WobbleStrokeRender {
    WobbleRenderer.glyphInk(stroke.d, stroke.width)?.let { ink ->
        return WobbleStrokeRender.Ink(wobbleInkPath(ink))
    }
    val plain =
        try {
            PathParser().parsePathString(stroke.d).toPath()
        } catch (e: Exception) {
            // glyphInk already rejected the d; an empty path draws nothing,
            // matching AndroidSVG's behavior for the same broken stroke.
            Path()
        }
    return WobbleStrokeRender.Plain(plain, stroke.width.toFloat())
}

/** Parses `"minX minY width height"`; null falls back to the markup pipeline. */
private fun parseGlyphViewBox(value: String): PebbleSvgModel.ViewBox? {
    val parts = value.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
    if (parts.size != 4) return null
    return PebbleSvgModel.ViewBox(parts[0], parts[1], parts[2], parts[3])
}

/** Compose [Color] to 6-digit `"#RRGGBB"` for SVG-text injection (drops alpha; glyph strokes are opaque). */
private fun rgbHex(color: Color): String = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

/**
 * Pure, JVM-testable SVG builder. Emits `stroke="currentColor"` so [PebbleSvg]
 * substitutes the palette hex. [GlyphStroke.width] appends via `Double.toString`
 * (locale-free `"6.0"`, never `"6,0"`), so the markup is stable across locales.
 */
internal fun buildGlyphSvg(
    strokes: List<GlyphStroke>,
    viewBox: String,
): String =
    buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
        append(viewBox)
        append("\">")
        strokes.forEach { stroke ->
            append("<path d=\"")
            append(stroke.d)
            append("\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"")
            append(stroke.width)
            append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
        }
        append("</svg>")
    }
