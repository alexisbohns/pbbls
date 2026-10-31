package app.pbbls.android.features.achievements.figures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.render.stone.StoneTones

private const val ON_TINT = 0.08f
private const val ON_STROKE = 1.3f
private const val OFF_STROKE = 1f
private const val DOT_HALO = 0.8f
private val DOT_ROOM = 10.dp

/**
 * The domains' mended band: an inked shard per domain that came, in the order it came (the
 * served `entered` reversed, [domainBandItems]), then a dotted shard per domain not yet there; a
 * new one has a dot under it. One node: [description].
 */
@Composable
fun ShardBand(
    items: List<BandItem>,
    description: String,
    modifier: Modifier = Modifier,
    width: Dp = 300.dp,
    height: Dp = 30.dp,
) {
    val colors = PebblesTheme.spine.colors
    val fill = remember(colors.ink, colors.stock) { StoneTones.mixOklab(colors.ink, ON_TINT, colors.stock) }
    // As wide as the room allows, up to [width], its height in proportion.
    Canvas(
        modifier
            .widthIn(max = width)
            .fillMaxWidth()
            .aspectRatio(width / (height + DOT_ROOM))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // Cut at the board's size and scaled whole: a narrower band is shorter too, so the new
        // dot under it stays inside the box.
        val k = size.width / width.value
        val shards = ShardBandGeometry.shards(items, width.value, height.value)
        withTransform({ scale(k, k, Offset.Zero) }) {
            shards.forEach { shard ->
                val path =
                    Path().apply {
                        shard.corners.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                        close()
                    }
                if (shard.on) {
                    drawPath(path, fill)
                    drawPath(path, colors.ink, style = Stroke(ON_STROKE, join = StrokeJoin.Round))
                } else {
                    drawPath(
                        path,
                        colors.ink2,
                        style = Stroke(OFF_STROKE, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f, 2.2f))),
                    )
                }
            }
            shards.filter { it.isNew }.forEach {
                drawCircle(colors.stock, ShardBandGeometry.DOT_R + DOT_HALO, it.dot)
                drawCircle(colors.ink, ShardBandGeometry.DOT_R, it.dot)
            }
        }
    }
}
