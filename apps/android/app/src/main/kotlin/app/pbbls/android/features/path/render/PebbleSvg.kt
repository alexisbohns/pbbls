package app.pbbls.android.features.path.render

import android.graphics.Picture
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.caverock.androidsvg.SVG

private const val TAG = "pebble-svg"

/**
 * Renders a server-composed pebble SVG string — the `PebbleRenderView` analog
 * (D10). The literal `currentColor` is string-replaced with [strokeHex]
 * (6-digit `#RRGGBB`) *before* AndroidSVG parses the markup, mirroring the iOS
 * substitution exactly.
 *
 * A malformed SVG logs once and renders nothing — the outline backdrop behind
 * the render still gives the row a silhouette, so failure degrades gracefully
 * instead of crashing the timeline.
 */
@Composable
fun PebbleSvg(
    svg: String,
    strokeHex: String,
    modifier: Modifier = Modifier,
) {
    SvgCanvas(markup = SvgColors.injectStrokeColor(svg, strokeHex), modifier = modifier)
}

/**
 * Parse + render-to-[Picture] once per markup string (via [remember]), then
 * replay the recorded picture scaled uniformly to fit the composable's bounds
 * and centered — the `.aspectRatio(contentMode: .fit)` analog. Callers control
 * sizing entirely through [modifier]. Shared by [PebbleSvg] (stroke-injected
 * `render_svg`) and [PebbleOutlineBackdrop] (fill-injected outline assets).
 */
@Composable
internal fun SvgCanvas(
    markup: String,
    modifier: Modifier = Modifier,
) {
    val picture = remember(markup) { parseToPicture(markup) }
    if (picture == null || picture.width <= 0 || picture.height <= 0) return

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val scale =
                minOf(
                    size.width / picture.width,
                    size.height / picture.height,
                )
            val dx = (size.width - picture.width * scale) / 2f
            val dy = (size.height - picture.height * scale) / 2f
            val native = canvas.nativeCanvas
            val checkpoint = native.save()
            native.translate(dx, dy)
            native.scale(scale, scale)
            native.drawPicture(picture)
            native.restoreToCount(checkpoint)
        }
    }
}

/**
 * Parse failures are runtime data conditions (a bad `render_svg` row), not
 * setup bugs — log and degrade, never crash. The broad catch is deliberate:
 * AndroidSVG can throw beyond `SVGParseException` (e.g. on unresolvable
 * internal references at render time), and every failure mode has the same
 * remedy here.
 */
private fun parseToPicture(markup: String): Picture? =
    try {
        SVG.getFromString(markup).renderToPicture()
    } catch (e: Exception) {
        Log.e(TAG, "SVG parse/render failed — rendering nothing", e)
        null
    }
