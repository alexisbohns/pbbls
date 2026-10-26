package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.PersonStat
import app.pbbls.android.core.ui.DaySetMarks
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

private const val SHOWN = 5

/** The month's five most present souls and a *more* to all of them (#1207, D6); an invitation when nobody is named. */
@Composable
internal fun PersonsSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    if (!MonthSections.isShown(MonthSection.PERSONS, wrap, lookups)) return
    val rows = MonthSections.namedPersons(wrap, lookups)
    Column {
        if (!onCard) {
            MonthSectionHead(MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, dayView))
        }
        if (rows.isEmpty()) {
            Text(copy.nobody(), style = type.lead, color = colors.ink, modifier = Modifier.padding(horizontal = SECTION_PAD_X))
            return@Column
        }
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            rows.take(SHOWN).forEach { MonthPersonRow(it, wrap, lookups, dayView, copy) }
        }
        val more = copy.morePersons(rows.size)
        MonthMore(more) { onMore(MonthSection.PERSONS) }
    }
}

/** A soul of the month: glyph, name, its pebbles, *new since* when the server says so, and its days. */
@Composable
internal fun MonthPersonRow(
    person: PersonStat,
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    copy: WrapCopy,
) {
    val colors = PebblesTheme.spine.colors
    val soul = lookups.soul(person.soulId) ?: return
    val fresh = if (person.newSincePrevious) copy.newSince(wrap.month.minusMonths(1)) else null
    MarkRow(
        title = soul.name,
        subtitle = fresh,
        count = person.count,
        description = listOfNotNull(soul.name, person.count.toString(), fresh).joinToString(", "),
        leading = { SoulMark(soul.glyph, colors.ink, Modifier.size(MARK)) },
        trailing = { DaySetMarks(person.days, wrap.dayMode(dayView), colors.ink, colors.ink3, ringSize = ROW_RING) },
    )
}
