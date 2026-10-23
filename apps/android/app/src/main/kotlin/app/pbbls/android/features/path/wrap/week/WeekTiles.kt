package app.pbbls.android.features.path.wrap.week

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.BarSegment
import app.pbbls.android.core.designsystem.CalendarLabels
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.ProportionBar
import app.pbbls.android.core.designsystem.TileCard
import app.pbbls.android.core.designsystem.TileHeader
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy

private val TILES_PAD = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
private val TILE_GAP = 8.dp
private val BODY_GAP = 6.dp
private val FACE = 18.dp
private val FACE_GAP = 2.dp
private val HOUR_DOT = 6.dp
private val HOUR_TRACK = 14.dp
private const val PIPS_PER_DAY = 4
private const val FACES = 5
private const val DOMAINS_SHOWN = 3

/**
 * The wrap of the week (#1207, D4): a row of small tiles, the ones the server listed in [WeekWrap.tiles]
 * and in its order. A tap opens the sheet on that tile's list. The live week's tiles are dashed
 * and read *so far*; each tile is one button that says its list in a sentence.
 *
 * @param days the feed's days of the week, for the contents tile's pips (display only).
 */
@Composable
fun WeekTiles(
    wrap: WeekWrap,
    days: List<SpineDay>,
    lookups: WrapLookups,
    onOpen: (WeekTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wrap.tiles.isEmpty()) return
    val copy = rememberWrapCopy()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(TILES_PAD),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        wrap.tiles.forEach { tile ->
            val body = tileSentence(tile, wrap, lookups, copy)
            val description = copy.tileDescription(tile, body)
            TileCard(
                dashed = wrap.live,
                onClick = { onOpen(tile) },
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick {
                            onOpen(tile)
                            true
                        }
                    },
            ) {
                TileHead(copy.tile(tile), count = tileCount(tile, wrap), live = wrap.live, copy = copy)
                Spacer(Modifier.height(BODY_GAP))
                when (tile) {
                    WeekTile.CONTENTS -> ContentsBody(days, lookups)
                    WeekTile.PERSONS -> PersonsBody(wrap, lookups, body)
                    WeekTile.EMOTIONS -> EmotionsBody(wrap, lookups, body)
                    WeekTile.DOMAINS -> DomainsBody(wrap, lookups)
                    WeekTile.WEIGHT -> WeightBody(wrap, lookups, copy)
                    WeekTile.HOURS -> HoursBody(wrap, lookups, copy)
                    WeekTile.KEPT -> KeptBody(wrap, copy)
                }
            }
        }
    }
}

@Composable
internal fun rememberWrapCopy(): WrapCopy {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) { WrapCopy(resources, locale) }
}

private fun tileCount(
    tile: WeekTile,
    wrap: WeekWrap,
): Int? =
    when (tile) {
        WeekTile.CONTENTS -> wrap.pebbles
        WeekTile.PERSONS -> wrap.persons?.size?.takeIf { it > 0 }
        WeekTile.EMOTIONS -> wrap.families?.size
        WeekTile.DOMAINS -> wrap.domains?.size
        WeekTile.WEIGHT, WeekTile.HOURS, WeekTile.KEPT -> null
    }

/** The sentence a tile says, for its semantics and, where it shows one, its caption. */
@Composable
internal fun tileSentence(
    tile: WeekTile,
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
): String =
    when (tile) {
        WeekTile.CONTENTS -> wrap.pebbles.toString()
        WeekTile.PERSONS -> {
            val names = wrap.persons.orEmpty().mapNotNull { lookups.soul(it.soulId)?.name }
            if (names.isEmpty()) copy.invitation(inviteNames(wrap, lookups), long = false) else names.joinToString(", ")
        }
        WeekTile.EMOTIONS -> copy.emotionsLead(familyNames(wrap.lead?.order.orEmpty(), lookups))
        WeekTile.DOMAINS -> copy.domainsLead(domainNames(wrap, lookups))
        WeekTile.WEIGHT -> wrap.weight?.let { copy.weightLead(it.bright.sum(), it.even.sum(), it.heavy.sum()) }.orEmpty()
        WeekTile.HOURS ->
            wrap.hours
                ?.takeIf { it.isNotEmpty() }
                ?.let { h ->
                    copy.hoursLead(h.minOf { it.happened }, h.maxOf { it.happened })
                }.orEmpty()
        WeekTile.KEPT ->
            wrap.kept
                ?.let { k ->
                    "${copy.photos(k.photoMomentIds.size)}, ${copy.cards(k.cards.sumOf { it.count })}"
                }.orEmpty()
    }

@Composable
internal fun familyNames(
    slugs: List<String>,
    lookups: WrapLookups,
): List<String> =
    slugs.map { slug ->
        ReferenceStrings.referenceName(
            ReferenceType.EMOTION_CATEGORY,
            slug,
            lookups.family(slug)?.name ?: slug,
        )
    }

@Composable
internal fun domainNames(
    wrap: WeekWrap,
    lookups: WrapLookups,
): List<String> =
    wrap.domains.orEmpty().mapNotNull { stat ->
        lookups.domain(stat.domainId)?.let { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name) }
    }

/** The moments the invitation names: the week's first three, by name. */
internal fun inviteNames(
    wrap: WeekWrap,
    lookups: WrapLookups,
): List<String> = wrap.inviteMomentIds.orEmpty().mapNotNull { id -> lookups.moment(id)?.name?.takeIf { it.isNotBlank() } }

@Composable
private fun TileHead(
    label: String,
    count: Int?,
    live: Boolean,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TileHeader(label, Modifier.alignByBaseline())
        when {
            live -> Text(copy.soFar(), style = type.sectionHint, color = colors.ink3, maxLines = 1, modifier = Modifier.alignByBaseline())
            count != null -> Text(count.toString(), style = type.contentsTime, color = colors.ink, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun Caption(
    text: String,
    italic: Boolean = false,
    maxLines: Int = 2,
) {
    val type = PebblesTheme.spine.type
    Text(
        text = text,
        style = if (italic) type.tileCaptionItalic else type.tileCaption,
        color = PebblesTheme.spine.colors.ink2,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Seven weekday columns, a pip per moment (its seal's bare silhouette), at most four a day. */
@Composable
private fun ContentsBody(
    days: List<SpineDay>,
    lookups: WrapLookups,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val weekStart = days.firstOrNull()?.date?.minusDays(days.first().index.toLong())
    val letters = remember(weekStart, locale) { weekStart?.let { CalendarLabels.weekdayLetters(it, locale) }.orEmpty() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0 until 7).forEach { index ->
            val moments =
                days
                    .firstOrNull { it.index == index }
                    ?.moments
                    .orEmpty()
                    .take(PIPS_PER_DAY)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(letters.getOrElse(index) { "" }, style = type.dayLetter, color = colors.ink3)
                moments.forEach { moment ->
                    StoneSeal(
                        valence = moment.valence,
                        palette = lookups.paletteFor(moment),
                        glyph = null,
                        knockout = colors.stock,
                        size = SealSize.PIP,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PersonsBody(
    wrap: WeekWrap,
    lookups: WrapLookups,
    sentence: String,
) {
    val colors = PebblesTheme.spine.colors
    val souls = wrap.persons.orEmpty().mapNotNull { lookups.soul(it.soulId) }
    if (souls.isEmpty()) {
        Box(Modifier.weight(1f)) { Caption(sentence, italic = true, maxLines = 4) }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FACE_GAP)) {
        souls.take(FACES).forEach { SoulMark(it.glyph, colors.ink, Modifier.size(FACE)) }
    }
    Spacer(Modifier.height(BODY_GAP))
    Caption(sentence)
}

@Composable
private fun EmotionsBody(
    wrap: WeekWrap,
    lookups: WrapLookups,
    sentence: String,
) {
    val segments = wrap.families.orEmpty().map { BarSegment(it.share.toFloat(), spotInk(lookups.family(it.slug)?.palette)) }
    ProportionBar(segments)
    Spacer(Modifier.height(BODY_GAP))
    Caption(sentence, italic = true)
}

@Composable
private fun DomainsBody(
    wrap: WeekWrap,
    lookups: WrapLookups,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val names = domainNames(wrap, lookups)
    wrap.domains.orEmpty().zip(names).take(DOMAINS_SHOWN).forEach { (stat, name) ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                style = type.tileCaption,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(stat.count.toString(), style = type.contentsTime, color = colors.ink3)
        }
    }
}

/** Bright, even and heavy as three medium seals in the week's lead family, each with its count. */
@Composable
private fun ColumnScope.WeightBody(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val weight = wrap.weight ?: return
    val palette =
        wrap.lead
            ?.order
            ?.firstOrNull()
            ?.let { lookups.family(it)?.palette }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        listOf(
            ValencePolarity.HIGHLIGHT to weight.bright,
            ValencePolarity.NEUTRAL to weight.even,
            ValencePolarity.LOWLIGHT to weight.heavy,
        ).forEach { (polarity, counts) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StoneSeal(valenceOf(ValenceSizeGroup.MEDIUM, polarity), palette, null, colors.stock, size = SealSize.CONTENTS)
                Text(counts.sum().toString(), style = type.contentsTime, color = colors.ink)
            }
        }
    }
    Caption(copy.weightCaption(), maxLines = 1)
}

/** The day's 24 hours as a track, a dot in the pebble's ink where each happened. */
@Composable
private fun HoursBody(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hours = wrap.hours.orEmpty()
    if (hours.isEmpty()) return
    val inks = hours.map { mark -> spotInk(lookups.moment(mark.momentId)?.let(lookups.paletteFor)) }
    Layout(
        content = { inks.forEach { Box(Modifier.size(HOUR_DOT).background(it, CircleShape)) } },
        modifier = Modifier.fillMaxWidth().height(HOUR_TRACK),
    ) { measurables, constraints ->
        val dot = HOUR_DOT.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, HOUR_TRACK.roundToPx()) {
            placeables.forEachIndexed { i, p ->
                val x = ((hours[i].happened / 24.0) * (constraints.maxWidth - dot)).toInt()
                p.place(x, (HOUR_TRACK.roundToPx() - dot) / 2)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("0", "12", "24").forEach { Text(it, style = type.dayLetter, color = colors.ink3) }
    }
    Spacer(Modifier.height(BODY_GAP))
    Caption(copy.hoursRange(hours.minOf { it.happened }, hours.maxOf { it.happened }), maxLines = 1)
}

@Composable
private fun KeptBody(
    wrap: WeekWrap,
    copy: WrapCopy,
) {
    val kept = wrap.kept ?: return
    Caption(copy.photos(kept.photoMomentIds.size), maxLines = 1)
    Caption(copy.cards(kept.cards.sumOf { it.count }), maxLines = 2)
}
