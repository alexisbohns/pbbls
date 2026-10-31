package app.pbbls.android.core.designsystem.stone

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A seeded rock at whatever size it is drawn: use it with `background`,
 * `clip` and a clickable's ripple. The seed is the cross-surface contract
 * (spec D5): the same seed and size draw the same stone on iOS, web and here.
 *
 * The outline is computed in dp, the unit iOS computes it in points, and
 * scaled to pixels, so the stone is the same at every density. A data class,
 * so two shapes with the same fields are equal and Compose keeps the cached
 * outline across recompositions. The stone is an object, not text, so it is
 * not mirrored right to left.
 */
data class StoneShape(
    val seed: String,
    val corner: Dp = 34.dp,
    val bow: Float = 1f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val scale = density.density
        val outline = StoneGeometry.stone(size / scale, seed, StoneOptions(corner = corner.value, bow = bow))
        return Outline.Generic(StoneGeometry.path(outline, scale))
    }
}
