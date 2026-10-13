package app.pbbls.android.core.ui.render

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import app.pbbls.android.R
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import java.util.concurrent.ConcurrentHashMap

/** Explicit (size, polarity) → seed resource, compile-checked like [OutlineAssets]. */
object StoneShapeAssets {
    private val ids: Map<Pair<ValenceSizeGroup, ValencePolarity>, Int> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to R.raw.shape_small_lowlight,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to R.raw.shape_small_neutral,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to R.raw.shape_small_highlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to R.raw.shape_medium_lowlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to R.raw.shape_medium_neutral,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to R.raw.shape_medium_highlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to R.raw.shape_large_lowlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to R.raw.shape_large_neutral,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to R.raw.shape_large_highlight,
        )

    fun resId(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Int = ids.getValue(size to polarity)
}

/** Nine small assets, parsed once per process. */
private val parsedShapes = ConcurrentHashMap<Int, PebbleSvgModel>()

@Composable
internal fun rememberStoneModel(
    valence: Valence,
    glyph: PebbleGlyph?,
    /** The blank stone: the silhouette outline only (R4). */
    outlineOnly: Boolean = false,
): PebbleSvgModel? {
    val resources = LocalResources.current
    val resId = StoneShapeAssets.resId(valence.sizeGroup, valence.polarity)
    val shape =
        remember(resId) {
            parsedShapes[resId]
                ?: resources
                    .openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
                    .let(StoneComposer::shapeModel)
                    ?.also { parsedShapes[resId] = it }
                    .also { if (it == null) Log.e("stone", "shape seed $resId did not parse") }
        }
    return remember(shape, glyph, valence, outlineOnly) {
        shape?.let {
            if (outlineOnly) StoneComposer.outlineOnly(it) else StoneComposer.compose(it, valence.sizeGroup, valence.polarity, glyph)
        }
    }
}
