package app.pbbls.android.features.path.spine

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.ui.render.stone.BLANK_STONE_PALETTE
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.spine.logic.CardSizes
import app.pbbls.android.features.path.spine.logic.EntryTypes
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** The card unit: a seventieth of the card's width, capped (rule set §Card sizing). */
private const val U_DIVISOR = 70f
private val U_MAX = 4.4.dp

private val RULE = 1.dp
private const val ASPECT_MS = 240
private const val RING_ALPHA = 0.30f
private const val RAIL_RULE_ALPHA = 0.5f

/** The photo's bed while it loads: the spot mixed into the stock. */
private const val PHOTO_BED = 0.18f

/** The seal's width in card units, by intensity. */
private val SEAL_UNITS = mapOf(1 to 3.8f, 2 to 4.4f, 3 to 4.9f)

/*
 * The field's valence pattern, the web's repeating gradients drawn as strokes:
 * rays from the stone's corner, ripples from it, contours from the far corner.
 */
private val RAY_STROKE = 1.dp
private const val RAY_STEP_DEG = 2.2
private const val RAY_ORIGIN_X = -0.06f
private const val RAY_ORIGIN_Y = -0.14f
private val PATTERN_STROKE = 0.8.dp
private val RIPPLE_FIRST = 5.9.dp
private val RIPPLE_PERIOD = 6.3.dp
private const val RIPPLE_ORIGIN_X = -0.04f
private const val RIPPLE_ORIGIN_Y = -0.10f
private val CONTOUR_FIRST = 6.4.dp
private val CONTOUR_PERIOD = 6.8.dp
private const val CONTOUR_ORIGIN_X = 1.12f
private const val CONTOUR_ORIGIN_Y = 1.22f
private const val CONTOUR_RX = 1.5f
private const val CONTOUR_RY = 1.1f

/** One layer of the web's `--spine-card-shadow`: `0 dy blur spread rgba(shadow, alpha)`. */
private class ShadowLayer(
    val dy: Dp,
    val blur: Dp,
    val spread: Dp,
    val alpha: Float,
)

private val PAPER_SHADOW =
    listOf(
        ShadowLayer(0.5.dp, 0.dp, 0.dp, 0.10f),
        ShadowLayer(1.dp, 2.dp, 0.dp, 0.07f),
        ShadowLayer(18.dp, 30.dp, (-22).dp, 0.55f),
    )
private val NIGHT_SHADOW =
    listOf(
        ShadowLayer(1.dp, 2.dp, 0.dp, 0.5f),
        ShadowLayer(18.dp, 30.dp, (-18).dp, 0.9f),
    )

/** Skia reads a `BlurMaskFilter` radius as `σ = 0.57735 × radius + 0.5`; CSS blur `b` is `σ = b / 2`. */
private const val SKIA_SIGMA_PER_RADIUS = 0.57735f
private const val SKIA_SIGMA_OFFSET = 0.5f
private const val MIN_BLUR_RADIUS_PX = 0.5f

/** The web's Night `--spine-photo-filter: brightness(.9) contrast(1.02)` as one matrix. */
private val NIGHT_PHOTO_FILTER: ColorFilter =
    run {
        val brightness = 0.9f
        val contrast = 1.02f
        val scale = brightness * contrast
        val shift = 0.5f * (1f - contrast) * 255f
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    scale,
                    0f,
                    0f,
                    0f,
                    shift,
                    0f,
                    scale,
                    0f,
                    0f,
                    shift,
                    0f,
                    0f,
                    scale,
                    0f,
                    shift,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            ),
        )
    }

/**
 * The Deck card (rule set §Card sizing, §Card anatomy): the photo at its own ratio,
 * never cropped, in a tinted field; the index and rail in the gutters, the title and
 * the people under it. Chrome in card units `u = min(width / 70, 4.4 dp)`. Until the
 * ratio is known the field is square, then it eases to the photo's shape. No click
 * of its own: the pebble wraps it.
 *
 * @param ratio the photo's width / height; null until the probe lands.
 * @param photoUrl the signed original; null until signed (the field waits, tinted).
 * @param thumbUrl the signed thumb, Coil's placeholder key while the original loads.
 * @param weekday the locale's short weekday, lowercase, without the point.
 * @param emotionName already localised; empty for a moment that is not a pebble yet.
 * @param people the souls strip, handed the card unit `u` so its type and glyphs scale with the card.
 */
@Composable
fun SpineCard(
    moment: PathMoment,
    palette: EmotionPalette?,
    ratio: Float?,
    photoUrl: String?,
    thumbUrl: String?,
    weekday: String,
    time: String,
    emotionName: String,
    people: (@Composable (u: Dp) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val density = LocalDensity.current
    val valence = moment.valence
    val intensity = valence.intensity
    val polarity = valence.polarity
    val night = colors.isNight
    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, night)
    val emotionInk = if (palette == null) colors.ink2 else spot
    val tint = remember(spot, colors) { StoneTones.mixOklab(spot, colors.tintK, colors.stock) }
    val bed = remember(spot, colors) { StoneTones.mixOklab(spot, PHOTO_BED, colors.stock) }
    val pattern = spot.copy(alpha = colors.patK)
    val ring = spot.copy(alpha = RING_ALPHA)
    // A moment with no emotion has no painted edge (the web's --edge is unset).
    val edge = palette?.let { StoneTones.edge(stone, polarity, night) }
    val shape = MaterialTheme.shapes.medium
    val shadowLayers = if (night) NIGHT_SHADOW else PAPER_SHADOW
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val reduceMotion = rememberReduceMotion()
    val animatedRatio by animateFloatAsState(
        targetValue = ratio ?: CardSizes.PENDING_RATIO,
        animationSpec = if (reduceMotion) snap() else tween(ASPECT_MS, easing = EaseOut),
        label = "spine-card-aspect",
    )
    val day = remember(moment.happenedAt) { moment.happenedAt.atZoneSameInstant(ZoneId.systemDefault()).dayOfMonth }
    val cut = EntryTypes.cardTitleCut(polarity)
    val hasEmotion = moment.pebble != null && emotionName.isNotEmpty()

    BoxWithConstraints(modifier) {
        val cardWidth = CardSizes.size(intensity, animatedRatio, maxWidth.value).width.dp
        val u = min(cardWidth / U_DIVISOR, U_MAX)
        val uSp = with(density) { u.toSp().value }
        val edgeThickness = CardSizes.edge(intensity).dp

        Box(
            Modifier
                .width(cardWidth)
                .drawBehind {
                    val radius = shape.topStart.toPx(size, this)
                    drawCardShadow(shadowLayers, colors.shadow, radius, shadowPaint)
                    if (edge != null) {
                        drawRoundRect(edge, topLeft = Offset(0f, edgeThickness.toPx()), size = size, cornerRadius = CornerRadius(radius))
                    }
                }.background(colors.stock, shape)
                .clip(shape)
                .drawBehind {
                    val inset = (u * 1.6f).toPx()
                    drawRoundRect(
                        colors.cardRule,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius((u * 1.3f).toPx()),
                        style = Stroke(RULE.toPx()),
                    )
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = u * 4.6f, start = u * 9f, end = u * 9f, bottom = u * 6.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Field(
                    u = u,
                    ratio = animatedRatio,
                    polarity = polarity,
                    tint = tint,
                    bed = bed,
                    pattern = pattern,
                    ring = ring,
                    photoUrl = photoUrl,
                    thumbUrl = thumbUrl,
                    night = night,
                    unavailableStyle = type.cardSoul(uSp),
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = u * 3.3f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A photo-only moment shows no title: the photo is the moment (spec D3).
                    moment.name?.let { name ->
                        Text(
                            text = name,
                            style =
                                type
                                    .cardTitle(
                                        uSp,
                                        cut.first,
                                        cut.second,
                                    ).copy(lineBreak = LineBreak.Heading, textAlign = TextAlign.Center),
                            color = colors.ink,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (people != null) {
                        Box(Modifier.fillMaxWidth().padding(top = u * 1.8f), contentAlignment = Alignment.Center) { people(u) }
                    }
                }
            }
            Column(
                Modifier
                    .offset(x = u * 2.1f, y = u * 4.3f)
                    .width(u * 6.6f)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = day.toString(), style = type.cardIndexNumeral(uSp), color = colors.ink)
                // The start padding balances the small caps' tracking, as the web's `pl-[.14em]` does.
                Text(
                    text = weekday,
                    style = type.cardIndexDay(uSp),
                    color = colors.ink,
                    modifier = Modifier.padding(top = u * 1.1f, start = u * 0.25f),
                )
                StoneSeal(
                    valence = valence,
                    palette = palette,
                    glyph = moment.pebble?.glyph,
                    knockout = colors.stock,
                    modifier = Modifier.padding(top = u * 1.4f).width(u * SEAL_UNITS.getValue(intensity)),
                )
            }
            Rail(
                u = u,
                time = time,
                emotionName = if (hasEmotion) emotionName else null,
                emotionInk = emotionInk,
                timeStyle = type.cardRail(uSp),
                emotionStyle = type.cardRailEmotion(uSp),
                rule = colors.ink2.copy(alpha = RAIL_RULE_ALPHA),
                ink = colors.ink2,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = -(u * 3.7f), y = u * 4.7f),
            )
        }
    }
}

/** The tinted mat and the photo in it, at the photo's ratio; "photo unavailable" when it fails. */
@Composable
private fun Field(
    u: Dp,
    ratio: Float,
    polarity: ValencePolarity,
    tint: Color,
    bed: Color,
    pattern: Color,
    ring: Color,
    photoUrl: String?,
    thumbUrl: String?,
    night: Boolean,
    unavailableStyle: TextStyle,
) {
    val colors = PebblesTheme.spine.colors
    val context = LocalPlatformContext.current
    val fieldRadius = u * 0.5f
    val fieldShape = remember(fieldRadius) { RoundedCornerShape(fieldRadius) }
    var failed by remember(photoUrl) { mutableStateOf(false) }
    val request =
        remember(photoUrl, thumbUrl, context) {
            photoUrl?.let { url ->
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .apply { thumbUrl?.let { placeholderMemoryCacheKey(MemoryCache.Key(it)) } }
                    .crossfade(true)
                    .build()
            }
        }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(tint)
            .drawBehind { drawFieldPattern(polarity, pattern) }
            .border(RULE, ring, fieldShape)
            .padding(u * 1.8f),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(bed),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Text(
                    text = stringResource(R.string.path_spine_photo_unavailable),
                    style = unavailableStyle.copy(textAlign = TextAlign.Center),
                    color = colors.error,
                    modifier = Modifier.padding(horizontal = u * 2f),
                )
            } else if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (night) NIGHT_PHOTO_FILTER else null,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The rail, read top to bottom: time | emotion. Decorative; the pebble's description says both. */
@Composable
private fun BoxScope.Rail(
    u: Dp,
    time: String,
    emotionName: String?,
    emotionInk: Color,
    timeStyle: TextStyle,
    emotionStyle: TextStyle,
    rule: Color,
    ink: Color,
    modifier: Modifier,
) {
    Row(
        modifier.clearAndSetSemantics {}.vertical(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = time, style = timeStyle, color = ink, maxLines = 1, softWrap = false)
        if (emotionName != null) {
            Box(
                Modifier
                    .padding(horizontal = u * 1.2f)
                    .size(RULE, u * 2.4f)
                    .background(rule),
            )
            Text(text = emotionName, style = emotionStyle, color = emotionInk, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Lays a row out on its side, reading top to bottom (the web's `writing-mode:
 * vertical-rl`): measured unbounded, reported with its sides swapped, then turned
 * a quarter clockwise about its centre.
 */
private fun Modifier.vertical(): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }.rotate(90f)

private fun DrawScope.drawCardShadow(
    layers: List<ShadowLayer>,
    shadow: Color,
    radius: Float,
    paint: android.graphics.Paint,
) {
    drawIntoCanvas { canvas ->
        layers.forEach { layer ->
            val spread = layer.spread.toPx()
            val dy = layer.dy.toPx()
            val blur = layer.blur.toPx()
            paint.color = shadow.copy(alpha = layer.alpha).toArgb()
            paint.maskFilter = if (blur > 0f) BlurMaskFilter(blurRadius(blur), BlurMaskFilter.Blur.NORMAL) else null
            canvas.nativeCanvas.drawRoundRect(
                -spread,
                dy - spread,
                size.width + spread,
                size.height + dy + spread,
                radius,
                radius,
                paint,
            )
        }
    }
}

/** A CSS blur radius, in px, as the `BlurMaskFilter` radius giving the same σ. */
private fun blurRadius(cssBlurPx: Float): Float {
    val sigma = cssBlurPx / 2f
    return ((sigma - SKIA_SIGMA_OFFSET) / SKIA_SIGMA_PER_RADIUS).coerceAtLeast(MIN_BLUR_RADIUS_PX)
}

/** The field's pattern by valence, clipped by the caller to the field. */
private fun DrawScope.drawFieldPattern(
    polarity: ValencePolarity,
    color: Color,
) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(PATTERN_STROKE.toPx())
    when (polarity) {
        ValencePolarity.HIGHLIGHT -> {
            // Rays every 2.2° from the stone's corner, phased from the web's `from 90deg` (screen 0°).
            val origin = Offset(RAY_ORIGIN_X * w, RAY_ORIGIN_Y * h)
            val length = hypot(w - origin.x, h - origin.y)
            val fromDeg = Math.toDegrees(atan2((-origin.y).toDouble(), (w - origin.x).toDouble()))
            val toDeg = Math.toDegrees(atan2((h - origin.y).toDouble(), (-origin.x).toDouble()))
            val rayStroke = RAY_STROKE.toPx()
            var k = floor(fromDeg / RAY_STEP_DEG).toInt()
            while (k * RAY_STEP_DEG <= toDeg) {
                val a = Math.toRadians(k * RAY_STEP_DEG)
                drawLine(color, origin, origin + Offset((cos(a) * length).toFloat(), (sin(a) * length).toFloat()), rayStroke)
                k++
            }
        }
        ValencePolarity.NEUTRAL -> {
            val centre = Offset(RIPPLE_ORIGIN_X * w, RIPPLE_ORIGIN_Y * h)
            val far = hypot(w - centre.x, h - centre.y)
            val period = RIPPLE_PERIOD.toPx()
            var r = RIPPLE_FIRST.toPx()
            while (r <= far) {
                drawCircle(color, r, centre, style = stroke)
                r += period
            }
        }
        ValencePolarity.LOWLIGHT -> {
            // Ellipses 150 % × 110 % from the far corner: circles under a vertical scale.
            val centre = Offset(CONTOUR_ORIGIN_X * w, CONTOUR_ORIGIN_Y * h)
            val sy = (CONTOUR_RY * h) / (CONTOUR_RX * w)
            val far = hypot(centre.x, centre.y / sy)
            val period = CONTOUR_PERIOD.toPx()
            scale(1f, sy, pivot = centre) {
                var r = CONTOUR_FIRST.toPx()
                while (r <= far) {
                    drawCircle(color, r, centre, style = stroke)
                    r += period
                }
            }
        }
    }
}
