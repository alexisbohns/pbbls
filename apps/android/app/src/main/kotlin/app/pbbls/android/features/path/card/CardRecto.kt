package app.pbbls.android.features.path.card

import android.icu.text.ListFormatter
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.stone.BLANK_STONE_PALETTE
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.spine.ASPECT_MS
import app.pbbls.android.features.path.spine.CARD_GLYPH_UNITS
import app.pbbls.android.features.path.spine.PeopleLine
import app.pbbls.android.features.path.spine.SpineCardFace
import app.pbbls.android.features.path.spine.logic.CardSizes
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.spine.logic.PeopleLines
import app.pbbls.android.features.path.spine.shortWeekday
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The slip's least height and padding, in card units (the synthesis's `.plate .slip`). */
private const val SLIP_MIN_UNITS = 26f
private const val SLIP_PAD_V_UNITS = 5f
private const val SLIP_PAD_H_UNITS = 4f

/** The slip's foot line: the mock's `0 1px 0` spot at 22 %. */
private const val SLIP_FOOT_ALPHA = 0.22f
private val SLIP_FOOT = 1.dp

/**
 * The recto (pebble card spec §Recto): the Spine card at the held card's width, every
 * soul named up to eight, and the leaf with the full dateline and the words. A pebble
 * with no photo shows a slip on the emotion's mat carrying the name, set as the entry
 * sets it, in place of the photo (F3).
 *
 * TalkBack reads the title (a heading), the date with the emotion, the souls, then the
 * words: the face is one traversal group, and the souls and words are its late stops.
 *
 * @param u the card unit, `width / 70`.
 * @param ratio the photo's width / height; null until probed (the field is square).
 * @param zone where the day, the time and the dateline fall.
 */
@Composable
internal fun CardRecto(
    detail: MomentDetail,
    palette: EmotionPalette?,
    u: Dp,
    ratio: Float?,
    photoUrl: String?,
    thumbUrl: String?,
    soulsById: Map<String, SoulWithGlyph>,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val moment = remember(detail) { detail.toPathMoment() }
    val pebble = detail.pebble

    val zoned = remember(detail.happenedAt, zone) { detail.happenedAt.atZoneSameInstant(zone) }
    val time = remember(zoned, locale) { zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    val weekday = remember(zoned, locale) { shortWeekday(zoned.dayOfWeek, locale) }
    val dateline = remember(detail.happenedAt, locale, zone) { cardDateline(detail.happenedAt, locale, zone) }
    val emotionName = pebble?.emotion?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) } ?: ""
    val datelineDescription =
        if (emotionName.isEmpty()) dateline else stringResource(R.string.card_dateline_emotion_a11y, dateline, emotionName)

    // No week here: the souls keep the server's order.
    val souls = pebble?.souls ?: emptyList()
    val line =
        remember(souls, locale) {
            val ids = souls.map { it.id }
            PeopleLines.of(ids, ids, locale, namedInFull = PeopleLines.NAMED_ON_CARD)
        }
    val namesById = remember(souls) { souls.associate { it.id to it.name } }
    // Everyone, once, as the locale lists them: the strip is read as one stop, not name by name.
    val names = remember(souls, locale) { if (souls.isEmpty()) "" else ListFormatter.getInstance(locale).format(souls.map { it.name }) }

    val stone = remember(palette) { palette?.toStonePalette() ?: BLANK_STONE_PALETTE }
    val spot = StoneTones.spot(stone, colors.isNight)
    val hasPhoto = detail.sortedSnaps.firstOrNull() != null
    val name = detail.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.path_spine_untitled)
    // The field eases to the photo's shape once the probe lands, as the Spine card's does.
    val reduceMotion = rememberReduceMotion()
    val animatedRatio by animateFloatAsState(
        targetValue = ratio ?: CardSizes.PENDING_RATIO,
        animationSpec = if (reduceMotion) snap() else tween(ASPECT_MS, easing = EaseOut),
        label = "card-recto-aspect",
    )
    val nameType = remember(moment.valence) { EntryTypes.entry(moment.valence.intensity, moment.valence.polarity).name }

    SpineCardFace(
        moment = moment,
        palette = palette,
        u = u,
        ratio = animatedRatio,
        photoUrl = photoUrl,
        thumbUrl = thumbUrl,
        weekday = weekday,
        time = time,
        emotionName = emotionName,
        zone = zone,
        modifier = modifier.semantics { isTraversalGroup = true },
        people =
            if (line.named.isEmpty()) {
                null
            } else {
                { cardU: Dp ->
                    val uSp = with(density) { cardU.toSp().value }
                    PeopleLine(
                        line = line,
                        namesById = namesById,
                        soulsById = soulsById,
                        style = type.cardMore(uSp),
                        glyphSize = with(density) { (cardU * CARD_GLYPH_UNITS).toSp() },
                        asStrip = true,
                        nameStyle = type.cardSoul(uSp),
                        moreStyle = type.cardMore(uSp),
                        othersStyle = type.cardOthers(uSp),
                        glyphInk = colors.ink,
                        // One merged stop: a traversal index on the strip's plain FlowRow would be ignored.
                        modifier =
                            Modifier.clearAndSetSemantics {
                                contentDescription = names
                                traversalIndex = RECTO_LATE
                            },
                    )
                }
            },
        leaf = { cardU ->
            CardLeaf(
                dateline = dateline,
                description = detail.description,
                u = cardU,
                datelineDescription = datelineDescription,
            )
        },
        slip =
            if (hasPhoto) {
                null
            } else {
                { cardU ->
                    val uSp = with(density) { cardU.toSp().value }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = cardU * SLIP_MIN_UNITS)
                            .drawBehind {
                                val foot = SLIP_FOOT.toPx()
                                drawRect(spot.copy(alpha = SLIP_FOOT_ALPHA), Offset(0f, size.height), size.copy(height = foot))
                            }.background(colors.stock)
                            .padding(vertical = cardU * SLIP_PAD_V_UNITS, horizontal = cardU * SLIP_PAD_H_UNITS),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            style =
                                SpineTypography
                                    .cardSlipName(uSp, nameType.fontWeight, nameType.italic)
                                    .copy(lineBreak = LineBreak.Heading, textAlign = TextAlign.Center),
                            color = colors.ink,
                            modifier = Modifier.fillMaxWidth().semantics { heading() },
                        )
                    }
                }
            },
    )
}
