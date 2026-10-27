package app.pbbls.android.features.path.wrap.month

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.LocalSnapURLCache
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import coil3.compose.AsyncImage
import kotlin.math.sqrt

/** A milestone polaroid's frames, in the box's own unit (dp at the call site). */
internal data class MilestoneSnapLayout(
    val cardWidth: Float,
    val cardHeight: Float,
    val windowWidth: Float,
    val windowHeight: Float,
    val margin: Float,
    val strip: Float,
)

/** The polaroid's pure geometry (iOS `MilestoneSnap.shape`/`.layout`). */
internal object MilestoneSnapShape {
    /** The box's width over its height: room for a landscape photo. */
    const val BOX = 1.15f

    /** The strip under the photo, of the box's height. */
    private const val STRIP = 0.24f
    const val TILT = 4f

    /** Square, 3:4 or 4:3, whichever is nearest by the geometric midpoints; square while unknown. */
    fun shape(ratio: Float): Float {
        if (!ratio.isFinite() || ratio <= 0f) return 1f
        if (ratio >= sqrt(4f / 3)) return 4f / 3
        if (ratio <= sqrt(3f / 4)) return 3f / 4
        return 1f
    }

    fun layout(
        height: Float,
        ratio: Float,
    ): MilestoneSnapLayout {
        val margin = maxOf(2f, height * 0.06f)
        val strip = height * STRIP
        val roomWidth = height * BOX - 2 * margin
        val roomHeight = height - margin - strip
        val safe = shape(ratio)
        val (w, h) =
            if (safe >= roomWidth / roomHeight) roomWidth to roomWidth / safe else roomHeight * safe to roomHeight
        return MilestoneSnapLayout(w + 2 * margin, margin + h + strip, w, h, margin, strip)
    }
}

/**
 * A milestone's photo as a mini polaroid (#1242, iOS D16): the stock under a rule, the photo in a
 * window of one of three shapes, cropped, a strip with two faint caption lines, tilted clockwise,
 * keeping within a box [height] tall. Until it loads, the window is a bed in [tint]. Decorative:
 * the row says the milestone.
 */
@Composable
internal fun MilestoneSnap(
    prefix: String,
    tint: Color,
    height: Dp,
    aspect: SnapAspectCache?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val urls = LocalSnapURLCache.current
    var url by remember(prefix) { mutableStateOf<String?>(null) }
    LaunchedEffect(prefix, urls) {
        if (url == null && urls != null) {
            url =
                runCatchingCancellable { urls.signedUrls(prefix).thumb }
                    .onFailure { Log.e(TAG, "snap sign failed for $prefix", it) }
                    .getOrNull()
        }
    }
    var ratio by remember(prefix) { mutableStateOf(aspect?.cached(prefix) ?: Float.NaN) }
    LaunchedEffect(prefix, aspect) {
        if (ratio.isNaN() && aspect != null) aspect.ratio(prefix)?.let { ratio = it }
    }
    val box = MilestoneSnapShape.layout(height.value, ratio)
    val shape = remember(height) { RoundedCornerShape(maxOf(2f, height.value * 0.05f).dp) }
    val caption = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.size(height * MilestoneSnapShape.BOX, height).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .rotate(MilestoneSnapShape.TILT)
                .size(box.cardWidth.dp, box.cardHeight.dp)
                .background(colors.stock, shape)
                .border(SpineLayoutTokens.RULE, colors.cardRule, shape)
                .padding(start = box.margin.dp, end = box.margin.dp, top = box.margin.dp),
        ) {
            Box(
                Modifier
                    .size(box.windowWidth.dp, box.windowHeight.dp)
                    .clipToBounds()
                    .background(tint.copy(alpha = 0.18f)),
            ) {
                url?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(box.windowWidth.dp, box.windowHeight.dp),
                        onError = { Log.w(TAG, "milestone photo failed for $prefix", it.result.throwable) },
                    )
                }
            }
            Column(
                Modifier.width(box.windowWidth.dp).height(box.strip.dp),
                verticalArrangement = Arrangement.spacedBy((box.strip * 0.18f).dp, Alignment.CenterVertically),
            ) {
                val line = maxOf(1f, height.value * 0.018f).dp
                Box(Modifier.fillMaxWidth(0.72f).height(line).background(caption, CircleShape))
                Box(Modifier.fillMaxWidth(0.46f).height(line).background(caption, CircleShape))
            }
        }
    }
}

private const val TAG = "wrap"
