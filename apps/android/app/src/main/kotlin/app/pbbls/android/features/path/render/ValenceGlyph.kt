package app.pbbls.android.features.path.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import app.pbbls.android.R
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import java.util.Locale

/**
 * (size, polarity) → raw valence-shape resource — compile-checked, like
 * [OutlineAssets]. Guards the `res/raw` name mapping (ValenceAssetsTest).
 */
object ValenceAssets {
    private val ids: Map<Pair<ValenceSizeGroup, ValencePolarity>, Int> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to R.raw.valence_small_lowlight,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to R.raw.valence_small_neutral,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to R.raw.valence_small_highlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to R.raw.valence_medium_lowlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to R.raw.valence_medium_neutral,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to R.raw.valence_medium_highlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to R.raw.valence_large_lowlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to R.raw.valence_large_neutral,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to R.raw.valence_large_highlight,
        )

    fun resId(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Int = ids.getValue(size to polarity)
}

/**
 * A monochrome valence pebble shape (outline + glyph) tinted to [tintColor] —
 * the iOS template-rendered `valence-*` asset analog. The bundled SVG authors
 * every stroke/fill as `currentColor` (ported + normalized from the web
 * `public/pebbles/<intensity>-<polarity>.svg` line art), so
 * [SvgColors.injectStrokeColor] paints the whole shape one color. Mirrors
 * [PebbleOutlineBackdrop].
 */
@Composable
fun ValenceGlyph(
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
    tintColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = ValenceAssets.resId(size, polarity)
    val hex = String.format(Locale.ROOT, "#%06X", tintColor.toArgb() and 0xFFFFFF)
    val markup =
        remember(resId, hex) {
            val raw =
                context.resources
                    .openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
            SvgColors.injectStrokeColor(raw, hex)
        }
    SvgCanvas(markup = markup, modifier = modifier)
}
