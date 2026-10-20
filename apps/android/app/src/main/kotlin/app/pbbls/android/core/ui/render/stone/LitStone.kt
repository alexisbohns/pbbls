package app.pbbls.android.core.ui.render.stone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * One stone's press, as the web's `useStonePress` plays it (#1122, spec §Press,
 * ripple, glint): the dip of the stone layer, the swell of the rings, the
 * sheen, sweep and spark of the glint. Each element is one progress
 * `Animatable` eased over its whole run — WAAPI's `element.animate(keyframes,
 * { duration, delay, easing })` eases the iteration, not each segment — and
 * the keyframe tables in [StoneMotion] map that progress to what the layers
 * read ([dipScale], [ringsScale], [sparkScale], [sparkRotation], [sparkAlpha]).
 * Every value rests where a still stone draws nothing — scale 1, alpha 0, the
 * sweep parked — and every animation is finite. The ripple is the caller's:
 * it mounts `StoneRipple` while [rippleToken] is set and hands the token back
 * through [rippleDone].
 */
@Stable
class StonePressState internal constructor(
    stillTouched: Boolean,
) {
    constructor() : this(stillTouched = false)

    val dipProgress = Animatable(0f)
    val ringsProgress = Animatable(0f)
    val sparkProgress = Animatable(0f)
    val sheen = Animatable(if (stillTouched) STILL_SHEEN else 0f)

    /** 0 parked off the stone, 1 travelled the glint's `travel`. */
    val sweep = Animatable(0f)

    val dipScale: Float
        get() = StoneMotion.piecewise(dipProgress.value, Dip.SCALE_FRAMES)

    val ringsScale: Float
        get() = StoneMotion.piecewise(ringsProgress.value, Rings.SCALE_FRAMES)

    val sparkScale: Float
        get() = StoneMotion.piecewise(sparkProgress.value, Spark.SCALE_FRAMES)

    val sparkRotation: Float
        get() = StoneMotion.piecewise(sparkProgress.value, Spark.ROTATION_FRAMES)

    val sparkAlpha: Float
        get() = StoneMotion.piecewise(sparkProgress.value, Spark.ALPHA_FRAMES)

    /** A press under reduced motion: the rings darken and a still sheen holds. */
    var touchedStill by mutableStateOf(stillTouched)
        private set

    /** Which still touch owns the hold: a second press extends it rather than cutting it. */
    private var stillGeneration = 0

    /** The live ripple, if any; a new press replaces it. */
    var rippleToken by mutableStateOf<Long?>(null)
        private set

    val isPressing: Boolean
        get() =
            dipProgress.isRunning ||
                ringsProgress.isRunning ||
                sparkProgress.isRunning ||
                sheen.isRunning ||
                sweep.isRunning

    /**
     * Plays the press. A second press while one runs restarts each element
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
            launch { dipProgress.run(Dip.DURATION, Dip.EASING) }
            launch {
                delay(Rings.DELAY.toLong())
                ringsProgress.run(Rings.DURATION, Rings.EASING)
            }
            launch {
                delay(Sweep.DELAY.toLong())
                sweep.run(Sweep.DURATION, Sweep.EASING)
                sweep.snapTo(0f)
            }
            launch {
                delay(Spark.DELAY.toLong())
                sparkProgress.run(Spark.DURATION, Spark.EASING)
            }
        }
    }

    private suspend fun pressStill() {
        val mine = ++stillGeneration
        touchedStill = true
        sheen.snapTo(STILL_SHEEN)
        try {
            delay(StoneMotion.STILL_TOUCH_MS)
        } finally {
            if (mine == stillGeneration) touchedStill = false
        }
        if (mine == stillGeneration) sheen.snapTo(0f)
    }

    /** The ripple for [token] has finished; a newer token stays. */
    fun rippleDone(token: Long) {
        if (rippleToken == token) rippleToken = null
    }

    companion object {
        /** The sheen at its own opacity (`StoneRecipes.glint(size).sheen.opacity`, the same for every size). */
        private val STILL_SHEEN: Float = StoneRecipes.glint(ValenceSizeGroup.MEDIUM).sheen.opacity

        /** A stone held under reduced motion, for previews: the rings dark, the sheen still. */
        internal fun stillTouched(): StonePressState = StonePressState(stillTouched = true)

        /** One WAAPI iteration: from 0, eased over the whole [duration] to 1. */
        private suspend fun Animatable<Float, AnimationVector1D>.run(
            duration: Int,
            easing: Easing,
        ) {
            snapTo(0f)
            animateTo(1f, tween(duration, easing = easing))
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
 * [width] defaults to the phone width for the stone's size; the card's verso paints
 * it larger. The rings and halo scale with the box, their strokes stay hairlines;
 * the carved glyph line scales with [width], so a larger stone carves heavier.
 * A stone at another [width] mounts its `StoneRipple` at the same width: the verso's
 * ripples once, when its card prints into a completed moment.
 */
@Composable
fun LitStone(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    seed: Long,
    press: StonePressState,
    modifier: Modifier = Modifier,
    width: Dp = StoneGeometry.STONE_WIDTH_DP.getValue(valence.sizeGroup).dp,
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
    val widthPx = with(density) { width.roundToPx() }
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
    val key = remember(spec, widthPx, heightPx, density.density) { StoneBitmapCache.key(spec, widthPx, heightPx, density.density) }
    // A preview has one frame: paint it there and then. On a device the first
    // paint leaves the main thread and the placeholder holds the frame. The
    // state is keyed, so a new key starts from its cache hit or the placeholder,
    // never from the previous stone.
    val inspection = LocalInspectionMode.current
    val bitmap =
        remember(key) {
            mutableStateOf(
                StoneBitmapCache.peek(key)
                    ?: if (inspection) StoneBitmapCache.get(spec, widthPx, heightPx, density.density, resources) else null,
            )
        }
    LaunchedEffect(key) {
        if (bitmap.value == null) {
            // CPU-bound rasterisation with nothing injectable in a composable: one of the
            // two call-site dispatchers DispatchersModule's KDoc allows.
            bitmap.value = withContext(Dispatchers.Default) { StoneBitmapCache.get(spec, widthPx, heightPx, density.density, resources) }
        }
    }
    val placeholder =
        remember(polarity, palette) {
            StoneTones.bodyRamp(polarity, palette?.toStonePalette() ?: BLANK_STONE_PALETTE)[1].color
        }

    Box(modifier.stoneBox(size, width)) {
        StoneRings(
            outline = outline,
            size = size,
            polarity = polarity,
            color = if (press.touchedStill) colors.ink2 else colors.ring,
            halo = colors.halo,
            scale = { press.ringsScale },
            modifier = Modifier.fillMaxSize(),
        )
        val stoneLayer =
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = press.dipScale
                scaleY = press.dipScale
            }
        val painted = bitmap.value
        if (painted != null) {
            PaintedStoneImage(painted, widthPx, heightPx, stoneLayer)
        } else {
            StonePlaceholder(outline = outline, size = size, color = placeholder, modifier = stoneLayer)
        }
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
 * The painted bitmap drawn with its box on this one, so its margin (the
 * shadow) paints past the box on every side. Not an `Image`: that clips to its
 * bounds, the hard edge the margin exists to remove. The layout box is the
 * stone's [widthPx] × [heightPx]; any other size scales the whole bitmap.
 */
@Composable
private fun PaintedStoneImage(
    painted: PaintedStone,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val sx = size.width / widthPx
        val sy = size.height / heightPx
        val margin = painted.margin
        drawImage(
            image = painted.image,
            dstOffset = IntOffset((-margin.left * sx).roundToInt(), (-margin.top * sy).roundToInt()),
            dstSize = IntSize((painted.image.width * sx).roundToInt(), (painted.image.height * sy).roundToInt()),
        )
    }
}

/** The body filled flat in the ramp's middle tone, until the painted bitmap arrives. */
@Composable
private fun StonePlaceholder(
    outline: StoneOutline,
    size: ValenceSizeGroup,
    color: Color,
    modifier: Modifier,
) {
    val box = StoneGeometry.outlineBox(size)
    Canvas(modifier) {
        val pxPerUnit = this.size.width / box.width
        withTransform({ scale(pxPerUnit, pxPerUnit, Offset.Zero) }) {
            drawPath(outline.path, color)
        }
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
        val sparkAlpha = press.sparkAlpha
        val sparkScale = press.sparkScale
        val sparkRotation = press.sparkRotation
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
