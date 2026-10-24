package app.pbbls.android.features.path.wrap.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.BarSegment
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.ProportionBar
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.designsystem.TabbedSheetPager
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.core.model.wrap.WeekWrap
import app.pbbls.android.core.ui.DayMode
import app.pbbls.android.core.ui.DaySetMarks
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.features.path.spine.PersonsOfWeek
import app.pbbls.android.features.path.spine.WeekContents
import app.pbbls.android.features.path.spine.logic.SpineWeek
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import java.time.ZoneId

private val PAGE_PAD_X = 20.dp
private val PAGE_PAD_TOP = 16.dp
private val PAGE_PAD_BOTTOM = 24.dp
private val SECTION_GAP = 22.dp
private val BAR_HEIGHT = 10.dp
private val GRID_LABEL = 64.dp
private val GRID_CELL = 84.dp
private const val EMPTY_CELL_ALPHA = 0.28f

/**
 * The week's half sheet (#1207, D4): one page per tile, in the tiles' order, opened on [initialTile].
 * Contents and persons are the Spine's own lists, rebased; the others share [MarkRow]. A contents
 * row closes the sheet and goes to its stone ([onGo]).
 *
 * @param readingDay the day being read on the page when the sheet opened, inked in the lists.
 */
@Composable
fun WeekSheet(
    wrap: WeekWrap,
    week: SpineWeek,
    zone: ZoneId,
    soulsById: Map<String, SoulWithGlyph>,
    lookups: WrapLookups,
    readingDay: () -> Int?,
    initialTile: WeekTile,
    onDismiss: () -> Unit,
    onGo: (PathMoment) -> Unit,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit = {},
) {
    val copy = rememberWrapCopy()
    TabbedSheetPager(
        tabs = wrap.tiles.map(copy::tile),
        initialPage = wrap.tiles.indexOf(initialTile),
        onDismiss = onDismiss,
        onPageChange = onPageChange,
        modifier = modifier,
    ) { index ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = PAGE_PAD_BOTTOM)) {
            when (wrap.tiles[index]) {
                WeekTile.CONTENTS ->
                    WeekContents(
                        week = week,
                        zone = zone,
                        paletteFor = lookups.paletteFor,
                        readingDay = readingDay,
                        onGo = { moment ->
                            onDismiss()
                            onGo(moment)
                        },
                    )
                WeekTile.PERSONS ->
                    if (wrap.persons.isNullOrEmpty()) {
                        Page { Lead(copy.invitation(inviteNames(wrap, lookups), long = true)) }
                    } else {
                        PersonsOfWeek(persons = wrap.persons, weekStart = wrap.weekStart, soulsById = soulsById, readingDay = readingDay)
                    }
                WeekTile.EMOTIONS -> Page { EmotionsPage(wrap, lookups, copy) }
                WeekTile.DOMAINS -> Page { DomainsPage(wrap, lookups, copy) }
                WeekTile.WEIGHT -> Page { WeightPage(wrap, lookups, copy) }
                WeekTile.HOURS -> Page { HoursPage(wrap, lookups, copy) }
                WeekTile.KEPT -> Page { KeptPage(wrap, lookups, copy) }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_PAD_X).padding(top = PAGE_PAD_TOP), content = content)
}

@Composable
private fun Lead(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = PebblesTheme.spine.type.lead,
        color = PebblesTheme.spine.colors.ink,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

@Composable
private fun ListHeader(label: String) {
    SpineSectionHeader(label = label, modifier = Modifier.padding(top = SECTION_GAP).rulePadding())
}

private fun Modifier.rulePadding(): Modifier = this.padding(bottom = 10.dp)

@Composable
private fun EmotionsPage(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val families = wrap.families.orEmpty()
    Lead(copy.emotionsLead(familyNames(wrap.lead?.order.orEmpty(), lookups)))
    ProportionBar(families.map { BarSegment(it.share.toFloat(), spotInk(lookups.family(it.slug)?.palette)) }, height = BAR_HEIGHT)
    ListHeader(copy.tile(WeekTile.EMOTIONS))
    val names = familyNames(families.map { it.slug }, lookups)
    families.zip(names).forEach { (family, name) ->
        val palette = lookups.family(family.slug)?.palette
        val ink = spotInk(palette)
        MarkRow(
            title = name,
            description = "$name, ${family.count}",
            count = family.count,
            leading = {
                StoneSeal(
                    valenceOf(ValenceSizeGroup.MEDIUM, ValencePolarity.HIGHLIGHT),
                    palette,
                    null,
                    colors.stock,
                    size = SealSize.PIP,
                )
            },
            trailing = { DaySetMarks(family.days, DayMode.Row, ink, colors.ink3) },
        )
    }
}

@Composable
private fun DomainsPage(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val names = domainNames(wrap, lookups)
    Lead(copy.domainsLead(names))
    ListHeader(copy.tile(WeekTile.DOMAINS))
    wrap.domains.orEmpty().filter { lookups.domain(it.domainId) != null }.zip(names).forEach { (domain, name) ->
        MarkRow(
            title = name,
            description = "$name, ${domain.count}",
            leading = {
                Text(
                    domain.count.toString(),
                    style = type.contentsTime,
                    color = colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            trailing = { DaySetMarks(domain.days, DayMode.Row, colors.ink, colors.ink3) },
        )
    }
}

/** Bright, even or heavy crossed with small, medium or large: a seal per cell, its count under it. */
@Composable
private fun WeightPage(
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
    Lead(copy.weightLead(weight.bright.sum(), weight.even.sum(), weight.heavy.sum()))
    val sizes =
        listOf(
            ValenceSizeGroup.SMALL to R.string.path_wrap_small,
            ValenceSizeGroup.MEDIUM to R.string.path_wrap_medium,
            ValenceSizeGroup.LARGE to R.string.path_wrap_large,
        )
    Row {
        Box(Modifier.width(GRID_LABEL))
        sizes.forEach { (_, label) ->
            Text(
                stringResource(label),
                style = type.dayLetter,
                color = colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    listOf(
        Triple(ValencePolarity.HIGHLIGHT, R.string.path_wrap_bright, weight.bright),
        Triple(ValencePolarity.NEUTRAL, R.string.path_wrap_even, weight.even),
        Triple(ValencePolarity.LOWLIGHT, R.string.path_wrap_heavy, weight.heavy),
    ).forEach { (polarity, label, counts) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = type.dayLetter, color = colors.ink2, modifier = Modifier.width(GRID_LABEL))
            sizes.forEachIndexed { i, (size, _) ->
                val n = counts.getOrElse(i) { 0 }
                Column(
                    modifier = Modifier.weight(1f).height(GRID_CELL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    val alpha = if (n == 0) EMPTY_CELL_ALPHA else 1f
                    StoneSeal(
                        valenceOf(size, polarity),
                        palette,
                        null,
                        colors.stock,
                        size = SealSize.MILESTONE,
                        modifier = Modifier.alpha(alpha),
                    )
                    Text(if (n == 0) "·" else n.toString(), style = type.contentsTime, color = colors.ink.copy(alpha = alpha))
                }
            }
        }
    }
}

/** Every pebble on the day's hours, then one row each, earliest first. */
@Composable
private fun HoursPage(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val hours = wrap.hours.orEmpty().sortedBy { it.happened }
    if (hours.isEmpty()) return
    Lead(copy.hoursLead(hours.first().happened, hours.last().happened))
    ListHeader(copy.tile(WeekTile.HOURS))
    hours.forEach { mark ->
        val moment = lookups.moment(mark.momentId) ?: return@forEach
        val name = moment.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.path_spine_untitled)
        val time = copy.time(mark.happened)
        MarkRow(
            title = name,
            titleStyle = type.contentsName(400, false),
            description = "$name, $time",
            leading = {
                StoneSeal(
                    moment.valence,
                    lookups.paletteFor(moment),
                    moment.pebble?.glyph,
                    colors.stock,
                    size = SealSize.CONTENTS,
                )
            },
            trailing = { Text(time, style = type.contentsTime, color = colors.ink3) },
        )
    }
}

@Composable
private fun KeptPage(
    wrap: WeekWrap,
    lookups: WrapLookups,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val kept = wrap.kept ?: return
    val untitled = stringResource(R.string.path_spine_untitled)

    @Composable
    fun row(
        momentId: String,
        trailing: String?,
    ) {
        val moment = lookups.moment(momentId) ?: return
        val name = moment.name?.takeIf { it.isNotBlank() } ?: untitled
        MarkRow(
            title = name,
            titleStyle = type.contentsName(400, false),
            description = listOfNotNull(name, trailing).joinToString(", "),
            leading = {
                StoneSeal(
                    moment.valence,
                    lookups.paletteFor(moment),
                    moment.pebble?.glyph,
                    colors.stock,
                    size = SealSize.CONTENTS,
                )
            },
            trailing = trailing?.let { { Text(it, style = type.contentsTime, color = colors.ink3) } },
        )
    }
    if (kept.photoMomentIds.isNotEmpty()) {
        SpineSectionHeader(label = copy.photos(kept.photoMomentIds.size), modifier = Modifier.rulePadding())
        kept.photoMomentIds.forEach { row(it, null) }
    }
    if (kept.cards.isNotEmpty()) {
        SpineSectionHeader(label = copy.cards(kept.cards.sumOf { it.count }), modifier = Modifier.padding(top = SECTION_GAP).rulePadding())
        kept.cards.forEach { row(it.momentId, copy.cardsShort(it.count)) }
    }
}
