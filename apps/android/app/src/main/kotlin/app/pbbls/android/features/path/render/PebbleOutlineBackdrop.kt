package app.pbbls.android.features.path.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import app.pbbls.android.R
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup

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
    val markup =
        remember(resId, fillHex) {
            val raw =
                context.resources
                    .openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
            SvgColors.injectOutlineFill(raw, fillHex)
        }
    SvgCanvas(markup = markup, modifier = modifier.alpha(fillOpacity))
}
