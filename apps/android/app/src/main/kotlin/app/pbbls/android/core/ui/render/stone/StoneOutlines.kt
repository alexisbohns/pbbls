package app.pbbls.android.core.ui.render.stone

import android.content.res.Resources
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine
import app.pbbls.android.core.ui.render.OutlineAssets

/**
 * The flat outline a stone's live Compose layers share (#1122): the asset's
 * text, its path data and the parsed path, all in outline units. The painter
 * keeps its own `android.graphics` copy of the body; this one is for `Canvas`
 * draws (rings, glint clip, ripple, seal).
 */
internal data class StoneOutline(
    val raw: String,
    val d: String,
    val path: Path,
)

/** The nine flat outlines, parsed once per process. */
internal object StoneOutlines {
    private val cache = HashMap<Int, StoneOutline>()

    fun of(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        resources: Resources,
    ): StoneOutline {
        val resId = OutlineAssets.resId(size, polarity)
        return synchronized(cache) {
            cache.getOrPut(resId) {
                parse(resources.openRawResource(resId).bufferedReader().use { it.readText() })
            }
        }
    }

    fun parse(raw: String): StoneOutline {
        val d = outlinePathData(raw)
        val path =
            try {
                PathParser().parsePathString(d).toPath()
            } catch (e: IllegalArgumentException) {
                Path()
            }
        if (raw.contains("fill-rule=\"evenodd\"")) path.fillType = PathFillType.EvenOdd
        return StoneOutline(raw, d, path)
    }
}

@Composable
internal fun rememberStoneOutline(
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
): StoneOutline {
    val resources = LocalResources.current
    return remember(size, polarity) { StoneOutlines.of(size, polarity, resources) }
}

/** The web draws a blank moment (no palette) as a neutral river pebble whatever its polarity. */
internal fun stonePolarity(
    polarity: ValencePolarity,
    blank: Boolean,
): ValencePolarity = if (blank) ValencePolarity.NEUTRAL else polarity

/** The stone's box: [width] (the phone width for its size by default), the height by the silhouette's aspect. */
internal fun Modifier.stoneBox(
    size: ValenceSizeGroup,
    width: Dp = StoneGeometry.STONE_WIDTH_DP.getValue(size).dp,
): Modifier {
    val box = StoneGeometry.outlineBox(size)
    return this.width(width).aspectRatio(box.width / box.height)
}

/** `scale × this` as a Compose matrix: the affine first, then a uniform scale (outline units → px). */
internal fun Affine.toComposeMatrix(scale: Float = 1f): Matrix =
    Matrix().also { m ->
        m.values[Matrix.ScaleX] = a * scale
        m.values[Matrix.SkewY] = b * scale
        m.values[Matrix.SkewX] = c * scale
        m.values[Matrix.ScaleY] = d * scale
        m.values[Matrix.TranslateX] = e * scale
        m.values[Matrix.TranslateY] = f * scale
    }

/** A transformed copy; the source is left as it is. */
internal fun Path.transformed(affine: Affine): Path =
    Path().also { copy ->
        copy.addPath(this)
        copy.fillType = fillType
        copy.transform(affine.toComposeMatrix())
    }
