package app.pbbls.android.core.ui.render.stone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.StoneMotion.Wave
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Three stone-shaped waves travelling out across the ground from a pressed
 * stone (#1122, spec §Press, ripple, glint). Mount it over the stone's box
 * while `StonePressState.rippleToken` is set and let it overflow; it calls
 * [onDone] with [token] after the last wave, and a still week holds none of
 * it. Under reduced motion the press sets no token, so this never mounts.
 * [blank] is the stone's own rule (no palette): the waves take the river
 * pebble's silhouette, as the stone did. [width] is the stone's, when it is drawn at
 * another than its size's (the card's verso): the waves travel in stone widths.
 */
@Composable
fun StoneRipple(
    valence: Valence,
    blank: Boolean,
    token: Long,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = StoneGeometry.STONE_WIDTH_DP.getValue(valence.sizeGroup).dp,
) {
    val size = valence.sizeGroup
    val polarity = stonePolarity(valence.polarity, blank)
    val outline = rememberStoneOutline(size, polarity)
    val frame = remember(outline, size, polarity) { StoneGeometry.ringGeometry(size, polarity, outline.d).frame }
    val ripple = PebblesTheme.spine.colors.ripple
    val waves = remember { List(WAVES) { WaveState() } }
    val scaleFrames = remember(size) { List(WAVES) { k -> Wave.scaleFrames(size, k) } }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(token) {
        coroutineScope {
            waves.forEachIndexed { k, wave ->
                launch {
                    wave.progress.snapTo(0f)
                    delay(Wave.delay(k).toLong())
                    // One WAAPI iteration: eased over the whole run, the keyframes linear in it.
                    wave.progress.animateTo(1f, tween(Wave.duration(k), easing = Wave.EASING))
                }
            }
        }
        done(token)
    }

    val box = StoneGeometry.outlineBox(size)
    Canvas(modifier.stoneBox(size, width)) {
        val pxPerUnit = this.size.width / box.width
        // The rings' own origin, the silhouette's centroid, so waves and rings share it.
        val centre = Offset(frame.cx * pxPerUnit, frame.cy * pxPerUnit)
        val strokePx = WAVE_STROKE.toPx()
        waves.forEachIndexed { k, wave ->
            val progress = wave.progress.value
            val alpha = StoneMotion.piecewise(progress, Wave.ALPHA_FRAMES)
            if (alpha <= 0f) return@forEachIndexed
            val scale = StoneMotion.piecewise(progress, scaleFrames[k])
            // The stroke is divided back to one screen dp.
            withTransform({
                scale(scale, scale, centre)
                scale(pxPerUnit, pxPerUnit, Offset.Zero)
            }) {
                drawPath(outline.path, ripple, alpha = alpha, style = Stroke(width = strokePx / (scale * pxPerUnit)))
            }
        }
    }
}

private class WaveState {
    val progress = Animatable(0f)
}

private const val WAVES = 3
private val WAVE_STROKE = 1.dp
