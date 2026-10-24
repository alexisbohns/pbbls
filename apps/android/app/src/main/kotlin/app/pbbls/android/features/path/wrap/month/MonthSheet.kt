package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.BarSegment
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.ProportionBar
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.designsystem.TabbedSheetPager
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.core.ui.DaySetMarks
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.stone.SealSize
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.week.familyNames
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import app.pbbls.android.features.path.wrap.week.spotInk
import app.pbbls.android.features.path.wrap.week.valenceOf
import kotlin.math.roundToInt

private val PAGE_PAD_X = 20.dp
private val PAGE_PAD_TOP = 16.dp
private val PAGE_PAD_BOTTOM = 24.dp
private val GAP = 14.dp
private val SWATCH = 10.dp
private val BAR_HEIGHT = 10.dp

/** The month sheet's tabs, in order (#1207, D6): each section's *more* opens its own. */
internal val MONTH_SHEET_TABS = listOf(MonthSection.PERSONS, MonthSection.GEM, MonthSection.DOMAINS)

/**
 * The month's half sheet (#1207, D6): persons · emotions · domains, each the full ranked list with
 * its days as a ring or a calendar ([ViewModeSwitch], remembered for every month).
 */
@Composable
internal fun MonthSheet(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    initial: MonthSection,
    onDismiss: () -> Unit,
    onPageChange: (MonthSection) -> Unit,
) {
    val copy = rememberWrapCopy()
    val tabs = MONTH_SHEET_TABS
    TabbedSheetPager(
        tabs =
            tabs.map {
                copy.tile(
                    when (it) {
                        MonthSection.PERSONS -> WeekTile.PERSONS
                        MonthSection.DOMAINS -> WeekTile.DOMAINS
                        else -> WeekTile.EMOTIONS
                    },
                )
            },
        initialPage = tabs.indexOf(initial).coerceAtLeast(0),
        onDismiss = onDismiss,
        onPageChange = { onPageChange(tabs[it]) },
    ) { index ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_PAD_X)
                .padding(top = PAGE_PAD_TOP, bottom = PAGE_PAD_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            when (tabs[index]) {
                MonthSection.PERSONS -> PersonsPage(wrap, lookups, dayView, onDayView, copy)
                MonthSection.DOMAINS -> DomainsPage(wrap, lookups, dayView, onDayView, copy)
                else -> EmotionsPage(wrap, lookups, dayView, onDayView, copy)
            }
        }
    }
}

@Composable
private fun Lead(text: String) {
    if (text.isNotEmpty()) Text(text, style = PebblesTheme.spine.type.lead, color = PebblesTheme.spine.colors.ink)
}

@Composable
private fun ListHead(
    label: String,
    dayView: DayView,
    copy: WrapCopy,
) {
    if (dayView == DayView.CIRCLE) {
        SpineSectionHeader(label = label, hint = copy.clockwiseHint())
    } else {
        SpineSectionHeader(label = label) { CalendarHead(Modifier.alignByBaseline()) }
    }
}

@Composable
private fun ColumnScope.PersonsPage(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    copy: WrapCopy,
) {
    val persons = wrap.persons.orEmpty().filter { lookups.soul(it.soulId) != null }
    if (persons.isEmpty()) {
        Lead(copy.nobody())
        return
    }
    val top = persons.take(2).mapNotNull { p -> lookups.soul(p.soulId)?.let { it.name to p.count } }
    val fresh = persons.filter { it.newSincePrevious }.mapNotNull { lookups.soul(it.soulId)?.name }
    Lead(copy.personsLead(top, fresh, wrap.month.minusMonths(1)))
    ViewModeSwitch(dayView, onDayView)
    ListHead(copy.tile(WeekTile.PERSONS), dayView, copy)
    Column { persons.forEach { MonthPersonRow(it, wrap, lookups, dayView, copy) } }
}

/** Families in the gem's order, each with its share and its fine emotions, by count. */
@Composable
private fun ColumnScope.EmotionsPage(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val fine = wrap.fineEmotions.orEmpty()
    if (fine.isEmpty()) return
    val order = wrap.gem?.lead?.order ?: fine.map { it.family }.distinct()
    val families = order.filter { slug -> fine.any { it.family == slug } }
    Lead(copy.emotionsLead(fine.size, families.size))
    wrap.gem?.let { gem ->
        ProportionBar(
            order.map { BarSegment((gem.shares[it] ?: 0.0).toFloat(), spotInk(lookups.family(it)?.palette)) },
            height = BAR_HEIGHT,
        )
    }
    ViewModeSwitch(dayView, onDayView)
    val names = familyNames(families, lookups)
    families.zip(names).forEach { (slug, name) ->
        val palette = lookups.family(slug)?.palette
        val ink = spotInk(palette)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(SWATCH).background(ink, CircleShape))
                Text(name, style = type.sectionLabel, color = colors.ink, modifier = Modifier.weight(1f))
                wrap.gem?.shares?.get(slug)?.let {
                    Text("${(it * 100).roundToInt()} %", style = type.contentsTime, color = colors.ink2, textAlign = TextAlign.End)
                }
            }
            fine.filter { it.family == slug }.forEach { stat ->
                val emotion = lookups.emotion(stat.emotionId)
                val label = emotion?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) } ?: return@forEach
                MarkRow(
                    title = label,
                    titleStyle = type.soulName,
                    count = stat.count,
                    description = "$label, ${stat.count}",
                    leading = {
                        StoneSeal(
                            valenceOf(ValenceSizeGroup.SMALL, ValencePolarity.HIGHLIGHT),
                            palette,
                            null,
                            colors.stock,
                            size = SealSize.PIP,
                        )
                    },
                    trailing = { DaySetMarks(stat.days, wrap.dayMode(dayView), ink, colors.ink3, ringSize = ROW_RING) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DomainsPage(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onDayView: (DayView) -> Unit,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val domains = wrap.domains.orEmpty().mapNotNull { stat -> lookups.domain(stat.domainId)?.let { stat to it } }
    if (domains.isEmpty()) return
    val names = domains.map { (_, d) -> ReferenceStrings.referenceName(ReferenceType.DOMAIN, d.slug, d.name) }
    Lead(copy.domainsLead(domains.size, names.first(), domains.first().first.count))
    ViewModeSwitch(dayView, onDayView)
    ListHead(copy.tile(WeekTile.DOMAINS), dayView, copy)
    Column {
        domains.zip(names).forEach { (pair, name) ->
            val stat = pair.first
            MarkRow(
                title = name,
                description = "$name, ${stat.count}",
                leading = {
                    Text(
                        stat.count.toString(),
                        style = type.contentsTime,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                trailing = { DaySetMarks(stat.days, wrap.dayMode(dayView), colors.ink, colors.ink3, ringSize = ROW_RING) },
            )
        }
    }
}
