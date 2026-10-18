package app.pbbls.android.features.path.spine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineLayout.COL_X
import app.pbbls.android.features.path.spine.SpineLayout.RAKE_TILE
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

private val RAKE_SHADOW_STROKE = 0.75.dp
private val RAKE_LIT_STROKE = 0.6.dp
private val MASK_FULL_UNTIL = 6.dp
private val MASK_FADED_FROM = 110.dp
private const val MASK_FADED_ALPHA = 0.22f

/**
 * The raked paper behind the days: the mock's six grooves, tiled, fading past the content
 * column. Draw it behind the feed, never on its own. One block of ground on its own
 * [SpineGroundElement]; the week's lazy rows each carry one, sharing a height map so the
 * rake runs on across them.
 */
@Composable
internal fun SpineGround(modifier: Modifier = Modifier) {
    val tile = rememberRakeTile()
    val heights = remember { mutableStateMapOf<Int, Int>() }
    Spacer(modifier.then(SpineGroundElement(tile, topInset = 0.dp, ground = 0, heights = heights, line = null)))
}

/** The rake tile at this density and scheme, painted once. */
@Composable
internal fun rememberRakeTile(): Bitmap {
    val colors = PebblesTheme.spine.colors
    val density = LocalDensity.current
    return remember(density, colors.rakeShadow, colors.rakeLight) {
        rakeTile(density, colors.rakeShadow, colors.rakeLight).asAndroidBitmap()
    }
}

/**
 * The rake over [width], masked from full at `COL_X − 6` to [MASK_FADED_ALPHA] at `COL_X +
 * 110`. The mask rides in the paint, not on the canvas: a `DstIn` draw inside an offscreen
 * layer went unmasked on device (Pixel 8) while Robolectric honoured it. Left-to-right
 * only: the Spine pins its layout direction (see `SpineWeek`).
 */
internal fun rakeBrush(
    tile: Bitmap,
    width: Float,
    density: Density,
): ShaderBrush {
    val rake = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val fullUntil = with(density) { (COL_X - MASK_FULL_UNTIL).toPx() }
    val fadedFrom = with(density) { (COL_X + MASK_FADED_FROM).toPx() }
    val mask =
        LinearGradient(
            0f,
            0f,
            fadedFrom,
            0f,
            intArrayOf(Color.Black.toArgb(), Color.Black.toArgb(), Color.Black.copy(alpha = MASK_FADED_ALPHA).toArgb()),
            floatArrayOf(0f, fullUntil / fadedFrom, 1f),
            Shader.TileMode.CLAMP,
        )
    return ShaderBrush(ComposeShader(rake, mask, PorterDuff.Mode.DST_IN))
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

/** The line down the margin, the length of every day. */
private val LINE_WIDTH = 1.dp

/**
 * A row on the ground: records its height for the rows below, draws the rake at the phase
 * the rows above left and the line down the margin, both from [topInset]. A node, not a
 * lambda chain, so the modifier compares equal across recompositions and the row keeps
 * its brush. [heights] is shared by every row of one ground and [ground] is the row's place
 * in it; a row whose measure is cached when those change replays its last height into them.
 */
internal data class SpineGroundElement(
    val tile: Bitmap,
    val topInset: Dp,
    val ground: Int,
    val heights: SnapshotStateMap<Int, Int>,
    val line: Color?,
) : ModifierNodeElement<SpineGroundNode>() {
    override fun create() = SpineGroundNode(tile, topInset, ground, heights, line)

    override fun update(node: SpineGroundNode) = node.update(tile, topInset, ground, heights, line)

    override fun InspectorInfo.inspectableProperties() {
        name = "spineGround"
        properties["ground"] = ground
    }
}

internal class SpineGroundNode(
    private var tile: Bitmap,
    private var topInset: Dp,
    private var ground: Int,
    private var heights: SnapshotStateMap<Int, Int>,
    private var line: Color?,
) : Modifier.Node(),
    DrawModifierNode,
    LayoutAwareModifierNode {
    private var brush: ShaderBrush? = null
    private var brushWidth = 0f

    /** The last measured height, −1 before the first; replayed when the map or the place changes. */
    private var lastHeight = -1

    fun update(
        tile: Bitmap,
        topInset: Dp,
        ground: Int,
        heights: SnapshotStateMap<Int, Int>,
        line: Color?,
    ) {
        if (tile !== this.tile) brush = null
        // A row whose size and constraints are unchanged hits the measure cache, so
        // `onRemeasured` would never reach a new map or a new place: hand it the last height.
        val replay = (ground != this.ground || heights !== this.heights) && lastHeight >= 0
        this.tile = tile
        this.topInset = topInset
        this.ground = ground
        this.heights = heights
        this.line = line
        if (replay) heights[ground] = lastHeight
        invalidateDraw()
    }

    override fun onRemeasured(size: IntSize) {
        lastHeight = size.height
        heights[ground] = size.height
    }

    override fun ContentDrawScope.draw() {
        val inset = topInset.toPx()
        if (size.width > 0f && size.height > inset) {
            val rake =
                brush?.takeIf { brushWidth == size.width }
                    ?: rakeBrush(tile, size.width, this).also {
                        brush = it
                        brushWidth = size.width
                    }
            // The shader sits in canvas space: shifting the canvas up by the phase and the
            // rect down by it keeps the rect in place while the tile's rows move on.
            val shift = (0 until ground).sumOf { heights[it] ?: 0 }.toFloat().mod(tile.height.toFloat())
            translate(top = -shift) {
                drawRect(rake, topLeft = Offset(0f, inset + shift), size = Size(size.width, size.height - inset))
            }
            line?.let { ink ->
                val x = SPINE_X.toPx()
                drawLine(ink, Offset(x, inset), Offset(x, size.height), LINE_WIDTH.toPx())
            }
        }
        drawContent()
    }
}
