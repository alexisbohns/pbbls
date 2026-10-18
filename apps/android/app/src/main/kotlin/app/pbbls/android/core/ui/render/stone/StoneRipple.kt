package app.pbbls.android.core.ui.render.stone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.render.stone.StoneMotion.Wave
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Three stone-shaped waves travelling out across the ground from a pressed
 * stone (#1122, spec §Press, ripple, glint). Mount it over the stone's box
 * while `StonePressState.rippleToken` is set and let it overflow; it calls
 * [onDone] with [token] after the last wave, and a still week holds none of
 * it. Under reduced motion the press sets no token, so this never mounts.
 */
@Composable
fun StoneRipple(
    valence: Valence,
    token: Long,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = valence.sizeGroup
    val outline = rememberStoneOutline(size, valence.polarity)
    val ripple = PebblesTheme.spine.colors.ripple
    val waves = remember { List(WAVES) { WaveState() } }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(token) {
        coroutineScope {
            waves.forEachIndexed { k, wave ->
                launch {
                    wave.scale.snapTo(Wave.WAVE_START_SCALE)
                    wave.alpha.snapTo(0f)
                    delay(Wave.delay(k).toLong())
                    val duration = Wave.duration(k)
                    val rise = (duration * Wave.WAVE_RISE_AT).roundToInt()
                    coroutineScope {
                        launch {
                            wave.scale.animateTo(
                                Wave.reach(size, k),
                                keyframes {
                                    durationMillis = duration
                                    Wave.WAVE_START_SCALE at 0 using Wave.EASING
                                    Wave.WAVE_RISE_SCALE at rise using Wave.EASING
                                    Wave.reach(size, k) at duration
                                },
                            )
                        }
                        launch {
                            wave.alpha.animateTo(
                                0f,
                                keyframes {
                                    durationMillis = duration
                                    0f at 0 using Wave.EASING
                                    Wave.WAVE_RISE_ALPHA at rise using Wave.EASING
                                    0f at duration
                                },
                            )
                        }
                    }
                }
            }
        }
        done(token)
    }

    val box = StoneGeometry.outlineBox(size)
    Canvas(modifier.stoneBox(size)) {
        val pxPerUnit = this.size.width / box.width
        val centre = this.center
        val strokePx = WAVE_STROKE.toPx()
        waves.forEach { wave ->
            val alpha = wave.alpha.value
            if (alpha <= 0f) return@forEach
            val scale = wave.scale.value
            // Scaled about the box centre; the stroke is divided back to one screen dp.
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
    val scale = Animatable(Wave.WAVE_START_SCALE)
    val alpha = Animatable(0f)
}

private const val WAVES = 3
private val WAVE_STROKE = 1.dp
