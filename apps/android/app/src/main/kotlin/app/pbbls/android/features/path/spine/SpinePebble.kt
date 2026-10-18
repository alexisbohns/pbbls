package app.pbbls.android.features.path.spine

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.common.UuidHash
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.LocalSnapURLCache
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.stone.LitStone
import app.pbbls.android.core.ui.render.stone.StoneGeometry
import app.pbbls.android.core.ui.render.stone.StoneMotion
import app.pbbls.android.core.ui.render.stone.StoneRipple
import app.pbbls.android.core.ui.render.stone.StoneTones
import app.pbbls.android.core.ui.render.stone.rememberStonePressState
import app.pbbls.android.core.ui.render.stone.toStonePalette
import app.pbbls.android.features.path.spine.SpineLayout.CARD_ANCHOR
import app.pbbls.android.features.path.spine.SpineLayout.COL_X
import app.pbbls.android.features.path.spine.SpineLayout.PAD_R
import app.pbbls.android.features.path.spine.SpineLayout.SPINE_X
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.spine.logic.PeopleLines
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TAG = "spine-pebble"

/** The leader from the line to a card, and the pebble's room below its stone. */
private val LEADER = 1.dp
private val STONE_CLEARANCE = 14.dp

/** The stone's hit area: the stone plus this all round, never under 48 dp (spec §Accessibility). */
private val HIT_INSET = 10.dp
private val MIN_TARGET = 48.dp

/** The card's people strip: glyphs of `4u`, type in card units. */
private const val CARD_GLYPH_UNITS = 4f

/**
 * One moment on the Spine (#1122, spec §The feed): its lit stone on the margin
 * line and, in the column, a Deck card when it has a photo or the entry set as
 * type when it has not. Pressing the stone dips it, ripples the ground and
 * glints, then opens the moment once the dip has landed (`OPEN_DELAY_MS`);
 * pressing the card or the entry opens at once and plays the stone's press all
 * the same. A long press on the card or entry offers to delete, as the row did.
 *
 * A moment that is not a pebble yet (`pebble == null`) is a blank stone at
 * medium size and is announced as "not a pebble yet". Every id here is the
 * moment's; the stone's grain is seeded by the pebble's id when there is one.
 *
 * @param palette the pebble's emotion palette; null for a blank stone.
 * @param personsOrder soul ids in persons-of-the-week order, for the people line.
 * @param aspect the session's ratio cache; null in previews.
 * @param pressRequests moment ids the week's contents ask to press (without opening).
 * @param ratioOverride a primed photo ratio, for previews with no cache.
 * @param photoOverride `(original, thumb)` URLs for previews; `null to null`
 *   shows the tinted field and never signs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpinePebble(
    moment: PathMoment,
    palette: EmotionPalette?,
    soulsById: Map<String, SoulWithGlyph>,
    personsOrder: List<String>,
    aspect: SnapAspectCache?,
    pressRequests: SharedFlow<String>,
    onOpen: (PathMoment) -> Unit,
    onRequestDelete: (PathMoment) -> Unit,
    modifier: Modifier = Modifier,
    ratioOverride: Float? = null,
    photoOverride: Pair<String?, String?>? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val reduceMotion by rememberUpdatedState(rememberReduceMotion())
    val haptics = rememberTapHaptics()
    val press = rememberStonePressState()

    val pebble = moment.pebble
    val valence = moment.valence
    val blank = pebble == null
    val prefix = moment.firstSnapPath
    val hasCard = prefix != null
    val size = valence.sizeGroup
    val entryType = remember(valence) { EntryTypes.entry(valence.intensity, valence.polarity) }
    val anchor = if (hasCard) CARD_ANCHOR else entryType.anchorDp.dp
    val stoneWidth = StoneGeometry.STONE_WIDTH_DP.getValue(size).dp
    val stoneHeight = StoneGeometry.outlineBox(size).let { stoneWidth * (it.height / it.width) }
    val seed = remember(pebble?.id, moment.id) { UuidHash.fnv1a(pebble?.id ?: moment.id) }

    val zoned = remember(moment.happenedAt) { moment.happenedAt.atZoneSameInstant(ZoneId.systemDefault()) }
    val date = remember(zoned, locale) { zoned.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)) }
    val time = remember(zoned, locale) { zoned.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    val weekday = remember(zoned, locale) { shortWeekday(zoned.dayOfWeek, locale) }

    val emotionName = pebble?.emotion?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) } ?: ""
    val emotionInk = remember(palette, colors) { palette?.let { StoneTones.spot(it.toStonePalette(), colors.isNight) } ?: colors.ink2 }

    val souls = pebble?.souls ?: emptyList()
    val line = remember(souls, personsOrder, locale) { PeopleLines.of(souls.map { it.id }, personsOrder, locale) }
    val namesById = remember(souls) { souls.associate { it.id to it.name } }
    // Everyone, as the web's `withNames` does, in the server's order.
    val names = remember(souls) { souls.map { it.name } }

    val description =
        stoneDescription(
            name = moment.name,
            date = date,
            time = time,
            kind = stringResource(stoneKindRes(size, valence.polarity)),
            emotion = emotionName,
            names = names,
            blank = blank,
            strings =
                StoneDescriptionStrings(
                    stone = stringResource(R.string.path_spine_stone_a11y),
                    blank = stringResource(R.string.path_spine_blank_a11y),
                    withNames = stringResource(R.string.path_spine_with_names),
                    untitled = stringResource(R.string.path_spine_untitled),
                ),
        )

    // The photo: signed once per prefix through the ambient cache, unless a preview hands the URLs in.
    val urls = LocalSnapURLCache.current
    val photo by produceState(photoOverride, prefix, urls) {
        if (value == null && urls != null && prefix != null) {
            value =
                runCatchingCancellable { urls.signedUrls(prefix).let { it.original to it.thumb } }
                    .onFailure { Log.e(TAG, "snap sign failed for $prefix", it) }
                    .getOrNull()
        }
    }
    val ratio by produceState(ratioOverride ?: prefix?.let { aspect?.cached(it) }, prefix, aspect) {
        if (value == null && aspect != null && prefix != null) value = aspect.ratio(prefix)
    }

    // The press choreography (spec §Press, ripple, glint): the stone opens once the dip
    // has landed; the card or entry opens at once and plays the press all the same.
    val pressStone = {
        haptics(TapHaptic.SELECTION)
        scope.launch {
            launch { press.press(reduceMotion) }
            delay(if (reduceMotion) 0L else StoneMotion.OPEN_DELAY_MS)
            onOpen(moment)
        }
        Unit
    }
    val openNow = {
        haptics(TapHaptic.SELECTION)
        onOpen(moment)
        scope.launch { press.press(reduceMotion) }
        Unit
    }
    LaunchedEffect(pressRequests, moment.id) {
        pressRequests.collect { id ->
            if (id == moment.id) launch { press.press(reduceMotion) }
        }
    }

    var menu by remember { mutableStateOf(false) }
    val stoneX = SPINE_X - COL_X - stoneWidth / 2
    val stoneY = anchor - stoneHeight / 2
    val hitWidth = max(stoneWidth + HIT_INSET * 2, MIN_TARGET)
    val hitHeight = max(stoneHeight + HIT_INSET * 2, MIN_TARGET)

    Box(
        modifier
            .fillMaxWidth()
            .padding(start = COL_X, end = PAD_R)
            .heightIn(min = anchor + stoneHeight / 2 + STONE_CLEARANCE)
            .testTag(JourneyTags.PATH_PEBBLE_ROW)
            .drawBehind {
                // A card hangs off its stone by a leader, from the line to the column's edge.
                if (hasCard) {
                    val y = anchor.toPx()
                    drawLine(colors.line, Offset(-(COL_X - SPINE_X).toPx(), y), Offset(0f, y), LEADER.toPx())
                }
            },
    ) {
        Box(
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = description }
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onLongClick = { menu = true },
                    onClick = openNow,
                ),
        ) {
            if (hasCard) {
                SpineCard(
                    moment = moment,
                    palette = palette,
                    ratio = ratio,
                    photoUrl = photo?.first,
                    thumbUrl = photo?.second,
                    weekday = weekday,
                    time = time,
                    emotionName = emotionName,
                    people =
                        if (line.named.isEmpty()) {
                            null
                        } else {
                            { u: Dp ->
                                val uSp = with(density) { u.toSp().value }
                                PeopleLine(
                                    line = line,
                                    namesById = namesById,
                                    soulsById = soulsById,
                                    style = type.cardMore(uSp),
                                    glyphSize = with(density) { (u * CARD_GLYPH_UNITS).toSp() },
                                    withLabel = false,
                                    nameStyle = type.cardSoul(uSp),
                                    moreStyle = type.cardMore(uSp),
                                    glyphInk = colors.ink,
                                )
                            }
                        },
                )
            } else {
                SpineEntry(
                    moment = moment,
                    emotionName = emotionName,
                    emotionInk = emotionInk,
                    people =
                        if (line.named.isEmpty()) {
                            null
                        } else {
                            {
                                PeopleLine(
                                    line = line,
                                    namesById = namesById,
                                    soulsById = soulsById,
                                    style = type.people,
                                    glyphSize = ENTRY_GLYPH_SIZE,
                                )
                            }
                        },
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.pebble_delete),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_trash),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menu = false
                        onRequestDelete(moment)
                    },
                )
            }
        }

        // The stone, centred on the line at the anchor: the ripple under it, overflowing
        // freely, then the lit stone in its exact box. Neither carries a click.
        press.rippleToken?.let { token ->
            StoneRipple(
                valence = valence,
                blank = blank,
                token = token,
                onDone = press::rippleDone,
                modifier = Modifier.offset(stoneX, stoneY),
            )
        }
        LitStone(
            valence = valence,
            palette = palette,
            glyph = pebble?.glyph,
            seed = seed,
            press = press,
            modifier = Modifier.offset(stoneX, stoneY),
        )
        // The hit box over it, invisible and wider than the stone: the box the stone
        // draws in stays exact, so growing the target never moves it off the line.
        Box(
            Modifier
                .offset(stoneX - (hitWidth - stoneWidth) / 2, stoneY - (hitHeight - stoneHeight) / 2)
                .size(hitWidth, hitHeight)
                .semantics { contentDescription = description }
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = pressStone),
        )
    }
}
