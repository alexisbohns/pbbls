package app.pbbls.android.core.ui.render.stone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.StoneMotion.Dip
import app.pbbls.android.core.ui.render.stone.StoneMotion.Rings
import app.pbbls.android.core.ui.render.stone.StoneMotion.Spark
import app.pbbls.android.core.ui.render.stone.StoneMotion.Sweep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One stone's press, as the web's `data-stone-*` hooks (#1122, spec §Press,
 * ripple, glint): the dip of the stone layer, the swell of the rings, the
 * sheen, sweep and spark of the glint. Every value rests where a still stone
 * draws nothing — scale 1, alpha 0, the sweep parked — and every animation is
 * finite. The ripple is the caller's: it mounts `StoneRipple` while
 * [rippleToken] is set and hands the token back through [rippleDone].
 */
@Stable
class StonePressState {
    val dip = Animatable(1f)
    val rings = Animatable(1f)
    val sheen = Animatable(0f)

    /** 0 parked off the stone, 1 travelled the glint's `travel`. */
    val sweep = Animatable(0f)
    val sparkScale = Animatable(0f)
    val sparkRotation = Animatable(0f)
    val sparkAlpha = Animatable(0f)

    /** A press under reduced motion: the rings darken and a still sheen holds. */
    var touchedStill by mutableStateOf(false)
        private set

    /** The live ripple, if any; a new press replaces it. */
    var rippleToken by mutableStateOf<Long?>(null)
        private set

    val isPressing: Boolean
        get() =
            dip.isRunning ||
                rings.isRunning ||
                sheen.isRunning ||
                sweep.isRunning ||
                sparkScale.isRunning ||
                sparkRotation.isRunning ||
                sparkAlpha.isRunning

    /**
     * Plays the press. A second press while one runs restarts each animation
     * (an `Animatable` cancels its previous `animateTo`). Under [reduceMotion]
     * nothing moves: the rings darken and the sheen holds, with no ripple.
     */
    suspend fun press(reduceMotion: Boolean) {
        if (reduceMotion) {
            pressStill()
            return
        }
        rippleToken = System.nanoTime()
        coroutineScope {
            launch { dip.animateTo(1f, DIP_SPEC) }
            launch {
                delay(Rings.DELAY.toLong())
                rings.animateTo(1f, RINGS_SPEC)
            }
            launch {
                delay(Sweep.DELAY.toLong())
                sweep.snapTo(0f)
                sweep.animateTo(1f, tween(Sweep.DURATION, easing = Sweep.EASING))
                sweep.snapTo(0f)
            }
            launch {
                delay(Spark.DELAY.toLong())
                coroutineScope {
                    launch { sparkScale.animateTo(0f, SPARK_SCALE_SPEC) }
                    launch { sparkRotation.animateTo(Spark.END_ROTATION, SPARK_ROTATION_SPEC) }
                    launch { sparkAlpha.animateTo(0f, SPARK_ALPHA_SPEC) }
                }
            }
        }
    }

    private suspend fun pressStill() {
        touchedStill = true
        try {
            sheen.snapTo(STILL_SHEEN)
            delay(StoneMotion.STILL_TOUCH_MS)
            sheen.snapTo(0f)
        } finally {
            touchedStill = false
        }
    }

    /** The ripple for [token] has finished; a newer token stays. */
    fun rippleDone(token: Long) {
        if (rippleToken == token) rippleToken = null
    }

    private companion object {
        const val STILL_SHEEN = 0.9f

        fun offsetAt(
            duration: Int,
            fraction: Float,
        ): Int = (duration * fraction).roundToInt()

        // CSS applies its timing function to every keyframe interval; so does `using` here.
        val DIP_SPEC: KeyframesSpec<Float> =
            keyframes {
                durationMillis = Dip.DURATION
                1f at 0 using Dip.EASING
                Dip.DIP_SCALE at offsetAt(Dip.DURATION, Dip.DIP_AT) using Dip.EASING
                Dip.OVERSHOOT_SCALE at offsetAt(Dip.DURATION, Dip.OVERSHOOT_AT) using Dip.EASING
                1f at Dip.DURATION
            }

        val RINGS_SPEC: KeyframesSpec<Float> =
            keyframes {
                durationMillis = Rings.DURATION
                1f at 0 using Rings.EASING
                Rings.SWELL_SCALE at offsetAt(Rings.DURATION, Rings.SWELL_AT) using Rings.EASING
                1f at Rings.DURATION
            }

        val SPARK_SCALE_SPEC: KeyframesSpec<Float> =
            keyframes {
                durationMillis = Spark.DURATION
                0f at 0 using Spark.EASING
                1f at offsetAt(Spark.DURATION, Spark.PEAK_AT) using Spark.EASING
                0f at Spark.DURATION
            }

        val SPARK_ROTATION_SPEC: KeyframesSpec<Float> =
            keyframes {
                durationMillis = Spark.DURATION
                0f at 0 using Spark.EASING
                Spark.PEAK_ROTATION at offsetAt(Spark.DURATION, Spark.PEAK_AT) using Spark.EASING
                Spark.END_ROTATION at Spark.DURATION
            }

        val SPARK_ALPHA_SPEC: KeyframesSpec<Float> =
            keyframes {
                durationMillis = Spark.DURATION
                0f at 0 using Spark.EASING
                1f at offsetAt(Spark.DURATION, Spark.PEAK_AT) using Spark.EASING
                0f at Spark.DURATION
            }
    }
}

@Composable
fun rememberStonePressState(): StonePressState = remember { StonePressState() }

/**
 * The pressable stone on the margin line (#1122, spec §The lit stone in
 * Compose): the rings and halo, the painted stone from [StoneBitmapCache],
 * and the live glint, three layers over one box so a press can dip the stone
 * and swell the rings without repainting. Nothing animates on its own.
 * Decorative: the caller's hit box carries the label and the click.
 *
 * A null [palette] is a moment with no emotion yet: a blank river pebble in
 * grey, uncarved, whatever its polarity. [seed] is `UuidHash.fnv1a(pebble.id)`.
 */
@Composable
fun LitStone(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    seed: Long,
    press: StonePressState,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val size = valence.sizeGroup
    val polarity = stonePolarity(valence.polarity, blank = palette == null)
    val outline = rememberStoneOutline(size, polarity)
    val density = LocalDensity.current
    val resources = LocalResources.current

    // The box is a fixed dp width, so its pixel size follows from the density
    // alone: the bitmap is ready on the first frame, with no layout round trip.
    val box = StoneGeometry.outlineBox(size)
    val widthPx =
        with(density) {
            StoneGeometry.STONE_WIDTH_DP
                .getValue(size)
                .dp
                .roundToPx()
        }
    val heightPx = (widthPx * box.height / box.width).roundToInt()
    val spec =
        remember(size, valence.polarity, palette, glyph, seed, colors) {
            StoneSpec(
                size = size,
                polarity = valence.polarity,
                palette = palette?.toStonePalette(),
                glyph = glyph,
                seed = seed,
                shadow = colors.shadow,
                shadowAlpha = colors.shadowAlpha,
                night = colors.isNight,
            )
        }
    val bitmap =
        remember(spec, widthPx, heightPx, density.density) {
            StoneBitmapCache.get(spec, widthPx, heightPx, density.density, resources)
        }

    Box(modifier.stoneBox(size)) {
        StoneRings(
            outline = outline,
            size = size,
            polarity = polarity,
            color = if (press.touchedStill) colors.ink2 else colors.ring,
            halo = colors.halo,
            scale = { press.rings.value },
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = press.dip.value
                    scaleY = press.dip.value
                },
        )
        StoneGlint(
            outline = outline,
            size = size,
            polarity = polarity,
            press = press,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The halo filled and the rings stroked: the flat outline scaled about its
 * centroid by the ring matrices, in a layer of its own so the swell leaves the
 * stone bitmap alone. Unclipped: the halo is wider than the box.
 */
@Composable
private fun StoneRings(
    outline: StoneOutline,
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
    color: Color,
    halo: Color,
    scale: () -> Float,
    modifier: Modifier,
) {
    val paths =
        remember(outline, size, polarity) {
            val geometry = StoneGeometry.ringGeometry(size, polarity, outline.d)
            RingPaths(
                rings = geometry.rings.map { outline.path.transformed(it) },
                halo = outline.path.transformed(geometry.halo),
            )
        }
    val box = StoneGeometry.outlineBox(size)
    Canvas(
        modifier.graphicsLayer {
            scaleX = scale()
            scaleY = scale()
        },
    ) {
        val pxPerUnit = this.size.width / box.width
        val stroke = Stroke(width = RING_STROKE.toPx() / pxPerUnit)
        withTransform({ scale(pxPerUnit, pxPerUnit, Offset.Zero) }) {
            drawPath(paths.halo, halo)
            paths.rings.forEach { drawPath(it, color, style = stroke) }
        }
    }
}

private class RingPaths(
    val rings: List<Path>,
    val halo: Path,
)

/**
 * The glint, clipped to the body at the polarity's strength: a sheen ellipse,
 * a sweep band crossing from the light, a four-point spark. Everything rests
 * invisible, so the draw is skipped whole until a press runs.
 */
@Composable
private fun StoneGlint(
    outline: StoneOutline,
    size: ValenceSizeGroup,
    polarity: ValencePolarity,
    press: StonePressState,
    modifier: Modifier,
) {
    val glint = remember(size) { StoneRecipes.glint(size) }
    val canvasAffine = remember(size, polarity) { StoneGeometry.canvasTransform(size, polarity) }
    val spark =
        remember(glint) {
            val path =
                try {
                    PathParser().parsePathString(glint.spark).toPath()
                } catch (e: IllegalArgumentException) {
                    Path()
                }
            path to path.getBounds().center
        }
    val sheenBrush =
        remember(glint) {
            Brush.radialGradient(
                0f to Color.White.copy(alpha = SHEEN_PEAK),
                1f to Color.White.copy(alpha = 0f),
                center = Offset.Zero,
                radius = glint.sheen.rx,
            )
        }
    val sweepBrush =
        remember(glint) {
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0f),
                0.5f to Color.White.copy(alpha = SWEEP_PEAK),
                1f to Color.White.copy(alpha = 0f),
                startX = glint.sweep.x,
                endX = glint.sweep.x + glint.sweep.width,
            )
        }
    val box = StoneGeometry.outlineBox(size)

    Canvas(modifier.alpha(StoneTones.glintStrength(polarity))) {
        if (!press.isPressing && !press.touchedStill) return@Canvas
        val pxPerUnit = this.size.width / box.width
        val sheenAlpha = press.sheen.value
        val sweepAt = press.sweep.value
        val sparkAlpha = press.sparkAlpha.value
        val sparkScale = press.sparkScale.value
        val sparkRotation = press.sparkRotation.value
        withTransform({ scale(pxPerUnit, pxPerUnit, Offset.Zero) }) {
            clipPath(outline.path) {
                withTransform({ transform(canvasAffine.toComposeMatrix()) }) {
                    if (sheenAlpha > 0f) {
                        val sheen = glint.sheen
                        withTransform({
                            rotate(sheen.rotate, Offset(sheen.cx, sheen.cy))
                            translate(sheen.cx, sheen.cy)
                            scale(1f, sheen.ry / sheen.rx, Offset.Zero)
                        }) {
                            drawCircle(sheenBrush, radius = sheen.rx, center = Offset.Zero, alpha = sheenAlpha)
                        }
                    }
                    if (sweepAt > 0f) {
                        val sweep = glint.sweep
                        withTransform({
                            rotate(sweep.rotate, Offset(sweep.cx, sweep.cy))
                            translate(sweepAt * glint.travel, 0f)
                        }) {
                            drawRect(sweepBrush, topLeft = Offset(sweep.x, sweep.y), size = Size(sweep.width, sweep.height))
                        }
                    }
                    if (sparkAlpha > 0f && sparkScale > 0f) {
                        val (path, centre) = spark
                        withTransform({
                            rotate(sparkRotation, centre)
                            scale(sparkScale, sparkScale, centre)
                        }) {
                            drawPath(path, Color.White, alpha = sparkAlpha)
                        }
                    }
                }
            }
        }
    }
}

private val RING_STROKE = 1.dp

/** The web's glint paints: `StoneSprite`'s sheen and sweep gradient stops. */
private const val SHEEN_PEAK = 0.75f
private const val SWEEP_PEAK = 0.72f
