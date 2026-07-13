package app.pbbls.android.features.path.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import app.pbbls.android.R
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.wobble.WobbleFlags
import app.pbbls.android.features.path.render.wobble.WobbleRenderer
import app.pbbls.android.features.path.render.wobble.wobbleInkPath

/**
 * Explicit (size, polarity) → raw-resource map for the nine bundled outline
 * silhouettes — compile-checked and greppable, like `ReferenceStrings` (D9).
 * The assets are byte-identical copies of
 * `apps/ios/Pebbles/Resources/Outlines/{size}-{polarity}.svg`, renamed to
 * Android's `[a-z0-9_]` resource alphabet (rename map in `apps/android/CLAUDE.md`).
 */
object OutlineAssets {
    private val ids: Map<Pair<ValenceSizeGroup, ValencePolarity>, Int> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to R.raw.outline_small_lowlight,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to R.raw.outline_small_neutral,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to R.raw.outline_small_highlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to R.raw.outline_medium_lowlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to R.raw.outline_medium_neutral,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to R.raw.outline_medium_highlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to R.raw.outline_large_lowlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to R.raw.outline_large_neutral,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to R.raw.outline_large_highlight,
        )

    fun resId(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Int = ids.getValue(size to polarity)
}

/**
 * The pebble silhouette rendered behind [PebbleSvg] — ports iOS
 * `PebbleOutlineBackdropView`: loads the bundled outline for the valence,
 * string-replaces the `#FF00FF` sentinel with [fillHex] (6-digit) before
 * parsing, and applies [fillOpacity] as view alpha (the fill color's alpha
 * cannot ride in a 6-digit hex).
 *
 * Wobble experiment (#555, debug-only via [WobbleFlags]): natively fills the
 * displaced silhouette instead of going through AndroidSVG; asset parse
 * failure or an unreadable fill hex falls through to the [SvgCanvas] branch.
 */
@Composable
fun PebbleOutlineBackdrop(
    sizeGroup: ValenceSizeGroup,
    polarity: ValencePolarity,
    fillHex: String,
    fillOpacity: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = OutlineAssets.resId(sizeGroup, polarity)
    val raw =
        remember(resId) {
            context.resources
                .openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }
        }
    val wobbleArt =
        remember(resId) {
            if (WobbleFlags.isEnabled) {
                WobbleRenderer.backdropArt("${sizeGroup.key}-${polarity.key}", raw)
            } else {
                null
            }
        }
    val fillColor = remember(fillHex) { EmotionPalette.parseColor(fillHex) }

    if (wobbleArt != null && fillColor != null) {
        val path =
            remember(resId) {
                wobbleInkPath(
                    contours = wobbleArt.contours,
                    fillType = if (wobbleArt.usesEvenOddFill) PathFillType.EvenOdd else PathFillType.NonZero,
                )
            }
        val viewBox = wobbleArt.viewBox
        Canvas(modifier = modifier.alpha(fillOpacity)) {
            if (viewBox.width <= 0f || viewBox.height <= 0f) return@Canvas
            // Same aspect-fit + centering as SvgCanvas / PebbleStaticRender.
            val fit = minOf(size.width / viewBox.width, size.height / viewBox.height)
            if (fit <= 0f) return@Canvas
            val dx = (size.width - viewBox.width * fit) / 2f - viewBox.minX * fit
            val dy = (size.height - viewBox.height * fit) / 2f - viewBox.minY * fit
            withTransform({
                translate(dx, dy)
                scale(fit, fit, pivot = Offset.Zero)
            }) {
                drawPath(path = path, color = fillColor, style = Fill)
            }
        }
        return
    }

    val markup = remember(resId, fillHex) { SvgColors.injectOutlineFill(raw, fillHex) }
    SvgCanvas(markup = markup, modifier = modifier.alpha(fillOpacity))
}
