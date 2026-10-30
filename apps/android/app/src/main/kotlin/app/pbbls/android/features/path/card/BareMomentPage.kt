package app.pbbls.android.features.path.card

import android.icu.text.DateTimePatternGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.rememberStoneOutline
import app.pbbls.android.core.ui.render.stone.stoneBox
import app.pbbls.android.features.path.spine.NIGHT_PHOTO_FILTER
import app.pbbls.android.features.path.spine.logic.CardSizes
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// The opening (the synthesis's `.bp-*`, the Gathering's `.m-*`).
private val TITLE_GAP = 10.dp
private val PHOTO_GAP = 20.dp
private val MAT_PADDING = 10.dp
private val WORDS_GAP = 16.dp
private val PLACE_GAP = 28.dp
private val BED_RULE = 1.dp

/**
 * The tallest the photo stands: the Spine's tallest card less its chrome (`CardSizes`, the
 * strongest intensity), so a portrait narrows rather than pushing the place below the fold.
 */
private val PHOTO_MAX_HEIGHT = (CardSizes.cap(STRONGEST) - CardSizes.CHROME_VERTICAL).dp
private const val STRONGEST = 3

// The place (the synthesis's `.slot`).
private const val PLACE_RATIO = 1.12f
private val PLACE_RADIUS = 16.dp
private val PLACE_BORDER = 1.dp
private val PLACE_DASH = 4.dp
private val PLACE_PAD_V = 28.dp
private val PLACE_PAD_H = 20.dp

/** The album corners' legs: under the 16 dp rounding a 16 dp corner shows as a sliver (the mock's are 24 px). */
private val CORNER = 24.dp
private val GHOST_WIDTH = 64.dp
private val GHOST_STROKE = 1.25.dp
private val GHOST_DASH = 4.dp
private val GHOST_TO_KICKER = 16.dp
private val KICKER_TO_BODY = 8.dp
private val BODY_PAD_H = 24.dp
private val BODY_TO_CTA = 22.dp

/**
 * A moment with no pebble has no card (pebble card spec §Bare moment): the Gathering's
 * plain opening, then the place its card will print into, with the page's one call to
 * action. [placeModifier] and [ghostModifier] let the completion find the place and its
 * dashed stone (`CardCompletion`).
 *
 * The opening is the kicker ("SUNDAY 11 OCTOBER · 09:40", in [zone]), the title, the
 * photo on a white mat when there is one, and the words. The place is Suit's "Not a
 * pebble yet": a dashed card outline held by four album corners, the stone as a dashed
 * outline, the kicker, one sentence and **Make it a pebble** ([onComplete]). It is at
 * least the card's 1 : 1.12 and grows with its words, so a large font scale is never
 * clipped. The ⋮ menu (Edit moment, Delete) stays the top bar's.
 *
 * TalkBack reads the kicker and title as one heading, then the photo, the words, the
 * place's kicker (a heading) and sentence, and the button.
 *
 * @param photoUrl the photo's signed original; null until signed, or with no photo.
 * @param ratio the photo's width / height; null until probed (the mat is square till then).
 * @param zone where the kicker's day and time fall, as the card's dateline does.
 */
@Composable
internal fun BareMomentPage(
    detail: MomentDetail,
    photoUrl: String?,
    ratio: Float?,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    placeModifier: Modifier = Modifier,
    ghostModifier: Modifier = Modifier,
    thumbUrl: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = PebblesTheme.spine.colors
    val locale = LocalConfiguration.current.locales[0]
    val (day, time) = remember(detail.happenedAt, locale, zone) { bareKicker(detail.happenedAt, locale, zone) }
    val name = detail.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.path_spine_untitled)
    val hasPhoto = detail.sortedSnaps.firstOrNull() != null
    val description = detail.description?.takeIf { it.isNotBlank() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CardLayout.PAGE_MARGIN)
            .wrapContentWidth()
            .widthIn(max = CardLayout.MAX_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // One heading stop: the day, then the title.
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append(day)
                        withStyle(SpanStyle(color = colors.ink3)) { append(" · ") }
                        withStyle(SpanStyle(color = colors.ink)) { append(time) }
                    },
                style = SpineTypography.kicker,
                color = colors.ink2,
                textAlign = TextAlign.Center,
                // A pause rather than the middle dot read out.
                modifier = Modifier.semantics { contentDescription = "$day, $time" },
            )
            Text(
                text = name,
                style = SpineTypography.bareTitle.copy(lineBreak = LineBreak.Heading),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = TITLE_GAP),
            )
        }
        if (hasPhoto) {
            MattedPhoto(
                photoUrl = photoUrl,
                thumbUrl = thumbUrl,
                ratio = ratio ?: CardSizes.PENDING_RATIO,
                modifier = Modifier.padding(top = PHOTO_GAP),
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = SpineTypography.cardLeafBody,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = WORDS_GAP),
            )
        }
        // The gap outside the caller's modifier, so the bounds it reports are the place's own.
        BarePlace(onComplete = onComplete, modifier = Modifier.padding(top = PLACE_GAP).then(placeModifier), ghostModifier = ghostModifier)
    }
}

/**
 * The photo on a white mat, at its ratio, whole ([ContentScale.Fit]), no taller than
 * [PHOTO_MAX_HEIGHT]: a portrait narrows, centred. At Night the bed carries a hairline, so
 * the mat reads before the photo lands.
 */
@Composable
private fun MattedPhoto(
    photoUrl: String?,
    thumbUrl: String?,
    ratio: Float,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val context = LocalPlatformContext.current
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
        modifier
            .widthIn(max = PHOTO_MAX_HEIGHT * ratio + MAT_PADDING * 2)
            .fillMaxWidth()
            .background(colors.stock)
            .padding(MAT_PADDING),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(colors.halo)
                .then(if (colors.isNight) Modifier.border(BED_RULE, colors.rule) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Text(
                    text = stringResource(R.string.path_spine_photo_unavailable),
                    style = SpineTypography.cardFooterNote.copy(textAlign = TextAlign.Center),
                    color = colors.error,
                )
            } else if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(R.string.photo_label),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (colors.isNight) NIGHT_PHOTO_FILTER else null,
                    onError = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** "Not a pebble yet": the place kept for the card, and the one call to action. */
@Composable
private fun BarePlace(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    ghostModifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    Column(
        modifier
            .fillMaxWidth()
            .minHeightForRatio(PLACE_RATIO)
            .drawBehind { drawPlace(colors.ink3, colors.rule) }
            .padding(vertical = PLACE_PAD_V, horizontal = PLACE_PAD_H),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GhostStone(ghostModifier)
        Text(
            text = stringResource(R.string.bare_place_kicker),
            style = SpineTypography.sectionLabel,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = GHOST_TO_KICKER).semantics { heading() },
        )
        Text(
            text = stringResource(R.string.bare_place_body),
            style = SpineTypography.placeBody,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KICKER_TO_BODY).padding(horizontal = BODY_PAD_H),
        )
        Button(
            onClick = onComplete,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.padding(top = BODY_TO_CTA),
        ) {
            PbblsIconImage(
                icon = PbblsIcon.PLUS_CIRCLE,
                contentDescription = null,
                size = ButtonDefaults.IconSize,
            )
            Text(stringResource(R.string.complete_cta), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/** The stone its card will carry, as a dashed outline: a medium river pebble, whatever comes. */
@Composable
private fun GhostStone(modifier: Modifier = Modifier) {
    val ink = PebblesTheme.spine.colors.ink3
    val outline = rememberStoneOutline(GHOST_SIZE, GHOST_POLARITY)
    val density = LocalDensity.current
    val dashes = remember(density) { ghostDashes(density) }
    Canvas(modifier.stoneBox(GHOST_SIZE, width = GHOST_WIDTH)) { drawGhostStone(outline.path, size.width, ink, dashes) }
}

/** The ghost's silhouette: a medium river pebble, the outline [drawGhostStone] takes. */
internal val GHOST_SIZE = ValenceSizeGroup.MEDIUM
internal val GHOST_POLARITY = ValencePolarity.NEUTRAL

/**
 * The ghost's dashes in outline units, [GHOST_DASH] long on the page's [GHOST_WIDTH] ghost:
 * made once per density, and growing with the outline when the completion flies it.
 */
internal fun ghostDashes(density: Density): PathEffect {
    val unit = with(density) { GHOST_WIDTH.toPx() } / StoneGeometry.outlineBox(GHOST_SIZE).width
    val dash = with(density) { GHOST_DASH.toPx() } / unit
    return PathEffect.dashPathEffect(floatArrayOf(dash, dash))
}

/**
 * The ghost stone's dashed [outline] (the [GHOST_SIZE] outline, in outline units) at
 * [width] px from the origin, with [dashes] from [ghostDashes]: the bare page draws it in
 * its place, the completion in flight to the card's clearing. Drawn under a scale, the
 * stroke divided back to [GHOST_STROKE], so a frame of the flight allocates no path.
 */
internal fun DrawScope.drawGhostStone(
    outline: Path,
    width: Float,
    ink: Color,
    dashes: PathEffect,
    alpha: Float = 1f,
) {
    val scale = width / StoneGeometry.outlineBox(GHOST_SIZE).width
    val stroke = GHOST_STROKE.toPx() / scale
    withTransform({ scale(scale, scale, Offset.Zero) }) {
        drawPath(outline, ink, alpha = alpha, style = Stroke(width = stroke, pathEffect = dashes))
    }
}

/** The dashed card outline and its four album corners, clipped to the outline's rounding. */
private fun DrawScope.drawPlace(
    border: Color,
    corner: Color,
) {
    val radius = CornerRadius(PLACE_RADIUS.toPx())
    val leg = CORNER.toPx()
    val rounded = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
    clipPath(rounded) {
        val w = size.width
        val h = size.height
        listOf(
            listOf(Offset(0f, 0f), Offset(leg, 0f), Offset(0f, leg)),
            listOf(Offset(w, 0f), Offset(w, leg), Offset(w - leg, 0f)),
            listOf(Offset(0f, h), Offset(0f, h - leg), Offset(leg, h)),
            listOf(Offset(w, h), Offset(w - leg, h), Offset(w, h - leg)),
        ).forEach { (a, b, c) ->
            drawPath(
                Path().apply {
                    moveTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    lineTo(c.x, c.y)
                    close()
                },
                corner,
            )
        }
    }
    val stroke = PLACE_BORDER.toPx()
    val dash = PLACE_DASH.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = border,
        topLeft = Offset(inset, inset),
        size = size.copy(width = size.width - stroke, height = size.height - stroke),
        cornerRadius = CornerRadius(radius.x - inset),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/**
 * At least as tall as `width / ratio`, and taller when the content needs it: a fixed
 * `aspectRatio` would clip the place's sentence at a large font scale on a narrow phone.
 */
private fun Modifier.minHeightForRatio(ratio: Float): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val free = measurable.measure(constraints)
            return@layout layout(free.width, free.height) { free.place(0, 0) }
        }
        val floor = (constraints.maxWidth / ratio).roundToInt().coerceAtMost(constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = max(constraints.minHeight, floor)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The bare page's kicker, as the locale writes a full weekday, its day and its month
 * ("Sunday 11 October", "dimanche 11 octobre"), and the short time, both in [zone].
 */
internal fun bareKicker(
    at: OffsetDateTime,
    locale: Locale,
    zone: ZoneId,
): Pair<String, String> {
    val zoned = at.atZoneSameInstant(zone)
    val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(DAY_SKELETON)
    val day = zoned.format(DateTimeFormatter.ofPattern(pattern, locale))
    val time = zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return day to time
}

private const val DAY_SKELETON = "EEEEdMMMM"
