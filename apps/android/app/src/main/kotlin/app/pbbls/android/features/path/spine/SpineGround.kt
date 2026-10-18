package app.pbbls.android.features.path.spine

import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineLayout.COL_X
import app.pbbls.android.features.path.spine.SpineLayout.RAKE_TILE
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

private val RAKE_SHADOW_STROKE = 0.75.dp
private val RAKE_LIT_STROKE = 0.6.dp
private val MASK_FULL_UNTIL = 6.dp
private val MASK_FADED_FROM = 110.dp
private const val MASK_FADED_ALPHA = 0.22f

/**
 * The raked paper behind the days: the mock's six grooves, tiled, fading past the content
 * column. Draw it behind the feed, never on its own.
 */
@Composable
fun SpineGround(modifier: Modifier = Modifier) {
    val colors = PebblesTheme.spine.colors
    val density = LocalDensity.current
    val tile =
        remember(density, colors.rakeShadow, colors.rakeLight) {
            rakeTile(density, colors.rakeShadow, colors.rakeLight).asAndroidBitmap()
        }
    Spacer(
        modifier.drawWithCache {
            if (size.width <= 0f || size.height <= 0f) return@drawWithCache onDrawBehind {}
            // The mask rides in the paint, not on the canvas: a `DstIn` draw inside an offscreen
            // layer went unmasked on device (Pixel 8) while Robolectric honoured it.
            val rake = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val fullUntil = (COL_X - MASK_FULL_UNTIL).toPx()
            val fadedFrom = (COL_X + MASK_FADED_FROM).toPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val mask =
                LinearGradient(
                    if (rtl) size.width else 0f,
                    0f,
                    if (rtl) size.width - fadedFrom else fadedFrom,
                    0f,
                    intArrayOf(Color.Black.toArgb(), Color.Black.toArgb(), Color.Black.copy(alpha = MASK_FADED_ALPHA).toArgb()),
                    floatArrayOf(0f, fullUntil / fadedFrom, 1f),
                    Shader.TileMode.CLAMP,
                )
            val brush = ShaderBrush(ComposeShader(rake, mask, PorterDuff.Mode.DST_IN))
            onDrawBehind { drawRect(brush) }
        },
    )
}

/** The 36 × 240 tile at this density, both path sets stroked in their colours, butt-capped as the SVG is. */
private fun rakeTile(
    density: Density,
    shadow: Color,
    light: Color,
): ImageBitmap {
    val width = with(density) { RAKE_TILE.width.roundToPx() }.coerceAtLeast(1)
    val height = with(density) { RAKE_TILE.height.roundToPx() }.coerceAtLeast(1)
    val bitmap = ImageBitmap(width, height)
    val canvas = BitmapCanvas(bitmap)
    // Scale to the rounded pixel size rather than by the raw density, so the tile repeats seamlessly.
    val toTile =
        Matrix().apply {
            scale(width / SpineRakePaths.TILE_WIDTH, height / SpineRakePaths.TILE_HEIGHT)
        }

    fun stroke(
        d: String,
        color: Color,
        strokeWidth: Float,
    ) {
        val path = PathParser().parsePathString(d).toPath().apply { transform(toTile) }
        val paint =
            Paint().apply {
                this.color = color
                this.strokeWidth = strokeWidth
                style = PaintingStyle.Stroke
                strokeCap = StrokeCap.Butt
                isAntiAlias = true
            }
        canvas.drawPath(path, paint)
    }
    with(density) {
        stroke(SpineRakePaths.SHADOW, shadow, RAKE_SHADOW_STROKE.toPx())
        stroke(SpineRakePaths.LIT, light, RAKE_LIT_STROKE.toPx())
    }
    return bitmap
}
