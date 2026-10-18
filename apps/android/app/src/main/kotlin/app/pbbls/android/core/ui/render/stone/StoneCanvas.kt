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
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.Affine
import app.pbbls.android.core.ui.render.OutlineAssets

/*
 * What the live Compose layers of a stone share (#1122): the flat outline as a
 * Compose Path in outline units, the box every stone layer fills, and the
 * Affine → Compose Matrix bridge. The painter keeps its own `android.graphics`
 * copies; these are for `Canvas` draws.
 */

/** The flat outline: its path data and the parsed path, both in outline units. */
internal data class StoneOutline(
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
        return StoneOutline(d, path)
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

/** The stone's box: the phone width for its size, the height by the silhouette's aspect. */
internal fun Modifier.stoneBox(size: ValenceSizeGroup): Modifier {
    val box = StoneGeometry.outlineBox(size)
    return width(StoneGeometry.STONE_WIDTH_DP.getValue(size).dp).aspectRatio(box.width / box.height)
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
