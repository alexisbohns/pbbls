package app.pbbls.android.features.path.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.pbbls.android.features.glyph.models.GlyphStroke
import java.util.Locale

/**
 * Renders a carved glyph — the iOS `GlyphView` analog (D13, pulled forward into
 * sub-project B so the read view's soul cells can render glyphs before the
 * create flow ships). Builds a minimal SVG from [strokes] and feeds it through
 * the existing [PebbleSvg] pipeline: strokes are authored as
 * `stroke="currentColor"`, which [PebbleSvg] substitutes with [strokeColor]'s
 * hex before AndroidSVG parses the markup. Reused by C's glyph picker and D's
 * form row.
 */
@Composable
fun GlyphImage(
    strokes: List<GlyphStroke>,
    viewBox: String,
    strokeColor: Color,
    modifier: Modifier = Modifier,
) {
    val markup = remember(strokes, viewBox) { buildGlyphSvg(strokes, viewBox) }
    PebbleSvg(svg = markup, strokeHex = rgbHex(strokeColor), modifier = modifier)
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
