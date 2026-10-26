package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.RevealMotion
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK_COLUMN
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_MIN
import app.pbbls.android.core.model.wrap.MonthSection
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.PersonStat
import app.pbbls.android.core.ui.DaySetMarks
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.copy.WrapCopy
import app.pbbls.android.features.path.wrap.intro.IntroBeat
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroLanding
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.IntroScore
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.intro.persons
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

private const val SHOWN = 5

/**
 * The month's five most present souls and a *more* to all of them (#1207, D6); an invitation when nobody is named.
 *
 * In the month intro (#1242) each row enters in turn: its glyph pops, its name rises, then its ring draws from
 * the 1st, a dot popping as the line reaches each day. On a card ([room], the card's room under its title band)
 * the rows share the room and *more* stands at its foot (D13); on the page, [room] is null and the rows keep
 * their size.
 */
@Composable
internal fun PersonsSection(
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    onMore: (MonthSection) -> Unit,
    onCard: Boolean = false,
    room: Dp? = null,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    if (!MonthSections.isShown(MonthSection.PERSONS, wrap, lookups)) return
    val rows = MonthSections.namedPersons(wrap, lookups)
    // On a card the section takes the room, so its weighted spacer can stand *more* at the foot.
    Column(if (room != null) Modifier.fillMaxHeight() else Modifier) {
        if (!onCard) {
            MonthSectionHead(MonthSections.head(MonthSection.PERSONS, wrap, lookups, copy, dayView))
        }
        if (rows.isEmpty()) {
            Text(
                copy.nobody(),
                style = type.lead,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = SECTION_PAD_X).introReveal(reveal, IntroKey.Lead),
            )
            return@Column
        }
        val shown = rows.take(SHOWN)
        val size = room?.let { PersonsSection.rowSize(it, shown.size) }
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            shown.forEachIndexed { index, person -> MonthPersonRow(person, wrap, lookups, dayView, copy, introRow = index, size = size) }
        }
        if (room != null) Spacer(Modifier.weight(1f))
        val more = copy.morePersons(rows.size)
        MonthMore(more, Modifier.introReveal(reveal, IntroKey.More)) { onMore(MonthSection.PERSONS) }
    }
}

/**
 * A soul of the month: glyph, name, its pebbles, *new since* when the server says so, and its days.
 *
 * @param introRow its place in the month page's persons, for the intro (#1242); the sheet leaves it null.
 * @param size its size on a card (D13); null, the list's.
 */
@Composable
internal fun MonthPersonRow(
    person: PersonStat,
    wrap: MonthWrap,
    lookups: WrapLookups,
    dayView: DayView,
    copy: WrapCopy,
    introRow: Int? = null,
    size: PersonsSection.RowSize? = null,
) {
    val colors = PebblesTheme.spine.colors
    val reveal = currentIntroReveal()
    // Settled (the sheet, or no clock), every part takes the core defaults: the row draws as ever.
    val row = introRow?.takeIf { reveal !== IntroReveal.settled }
    val soul = lookups.soul(person.soulId) ?: return
    val fresh = if (person.newSincePrevious) copy.newSince(wrap.month.minusMonths(1)) else null
    val glyph = size?.glyph ?: MARK
    val mode = wrap.dayMode(dayView)
    val ringSize = size?.ring ?: ROW_RING
    // The glyph pops; the name, its count, *new since* and the rule land as one; the ring lands itself.
    val words = Modifier.introReveal(reveal, row?.let(IntroKey::Name))
    MarkRow(
        title = soul.name,
        subtitle = fresh,
        count = person.count,
        description = listOfNotNull(soul.name, person.count.toString(), fresh).joinToString(", "),
        leading = { SoulMark(soul.glyph, colors.ink, Modifier.size(glyph)) },
        trailing = {
            if (row == null) {
                DaySetMarks(person.days, mode, colors.ink, colors.ink3, ringSize = ringSize)
            } else {
                DaySetMarks(
                    person.days,
                    mode,
                    colors.ink,
                    colors.ink3,
                    ringSize = ringSize,
                    ringProgress = { RevealMotion.easeInOut(reveal(IntroKey.Ring(row)).toDouble()).toFloat() },
                    // The row's and the calendar's empty days fade in over the ring's span (iOS).
                    gridAlpha = { RevealMotion.easeOut(reveal(IntroKey.Ring(row)).toDouble()).toFloat() },
                    dotProgress = { reveal(IntroKey.Dot(row, it)) },
                )
            }
        },
        minHeight = size?.height ?: ROW_MIN,
        // A glyph grown past the mark column widens it (iOS).
        markWidth = max(glyph, MARK_COLUMN),
        leadingModifier = Modifier.introReveal(reveal, row?.let(IntroKey::Glyph), IntroLanding.POP),
        titleModifier = words,
        subtitleModifier = words,
        countModifier = words,
        // The rule fades in with the words (iOS: the rule's `.fade` on the row's text key).
        ruleAlpha = if (row == null) ({ 1f }) else ({ RevealMotion.easeOut(reveal(IntroKey.Name(row)).toDouble()).toFloat() }),
    )
}

/** The persons' pure parts (#1242; iOS `PersonsSection`'s statics). */
internal object PersonsSection {
    /** A person's row on a card: its share of the room, within the row minimum and this (D13). */
    val ROW_CAP = 96.dp
    val GLYPH_CAP = 48.dp
    val RING_CAP = 60.dp

    /** *more*'s room at the card's foot (iOS `MonthLayout.moreReserve`). */
    val MORE_RESERVE = 56.dp

    /** A person's row on a card: its height, its glyph's side and its ring's. */
    data class RowSize(
        val height: Dp,
        val glyph: Dp,
        val ring: Dp,
    )

    /** The persons' intro: the head, then each of the five in turn; the invitation when nobody is named. */
    fun introBeats(
        wrap: MonthWrap,
        lookups: WrapLookups,
    ): List<IntroBeat> {
        val rows = MonthSections.namedPersons(wrap, lookups)
        return IntroScore.persons(rows.take(SHOWN).map { it.days }, hasLead = rows.isEmpty())
    }

    /** A person's row on a card (D13): its share of the room left by *more*, the glyph and the ring growing with it, each within its bounds. */
    fun rowSize(
        room: Dp,
        rows: Int,
    ): RowSize {
        val height = ((room - MORE_RESERVE) / maxOf(rows, 1)).coerceIn(ROW_MIN, ROW_CAP)
        return RowSize(
            height,
            (height * 0.5f).coerceIn(MARK, GLYPH_CAP),
            (height * 0.7f).coerceIn(ROW_RING, RING_CAP),
        )
    }
}
