package app.pbbls.android.features.path.spine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.designsystem.CalendarLabels
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT
import app.pbbls.android.core.designsystem.SpineLayoutTokens.DOT_GAP
import app.pbbls.android.core.designsystem.SpineLayoutTokens.MARK
import app.pbbls.android.core.designsystem.SpineLayoutTokens.ROW_MIN
import app.pbbls.android.core.designsystem.SpineSectionHeader
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.wrap.PersonStat
import app.pbbls.android.core.ui.DayMode
import app.pbbls.android.core.ui.DaySetMarks
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.features.path.spine.SpineLayout.FOLD_INDENT
import app.pbbls.android.features.path.spine.SpineLayout.PERSONS_HEADER_GAP
import app.pbbls.android.features.path.spine.SpineLayout.PERSONS_TOP
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X
import app.pbbls.android.features.path.spine.logic.PersonsOfWeek
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import java.time.LocalDate

/**
 * Who was there (#1122, spec §The opening; the web's `PersonsOfWeek.tsx`): each soul of
 * the week with its glyph, its name in small caps and seven dots for the days it appears
 * on, under a header of weekday letters right-aligned over the dots. Eight rows, then the
 * rest behind "and *N* others", so a fourteen-person picnic does not push the contents off
 * the first screen. Nothing when the week names nobody.
 *
 * @param persons the server's ranked persons (#1207); a soul missing from [soulsById] is skipped.
 * @param soulsById the souls' names and glyphs.
 * @param readingDay 0 = Monday: its letter in the header turns ink; read by the letters alone.
 */
@Composable
internal fun PersonsOfWeek(
    persons: List<PersonStat>,
    weekStart: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    readingDay: () -> Int?,
    modifier: Modifier = Modifier,
) {
    val rows = remember(persons, soulsById) { persons.mapNotNull { stat -> soulsById[stat.soulId]?.let { PersonRowModel(it, stat) } } }
    if (rows.isEmpty()) return
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val (shown, rest) = remember(rows) { PersonsOfWeek.split(rows) }
    val letters = remember(weekStart, locale) { CalendarLabels.weekdayLetters(weekStart, locale) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = VERSO_PAD_X).padding(top = PERSONS_TOP)) {
        SpineSectionHeader(label = stringResource(R.string.path_spine_persons)) {
            DayLetters(letters, readingDay, Modifier.alignByBaseline())
        }
        Column(Modifier.padding(top = PERSONS_HEADER_GAP).rule(colors.rule, above = true)) {
            shown.forEach { person -> PersonRow(person, weekStart) }
        }
        if (rest.isNotEmpty()) {
            val words = remember(rest.size, locale) { NumberWords.cardinal(rest.size, locale, NumberWords.Gender.FEMININE) }
            // The web's <details>: a button that says whether it is open, with the matching action.
            val state = stringResource(if (expanded) R.string.path_spine_persons_expanded else R.string.path_spine_persons_collapsed)
            Text(
                text = pluralStringResource(R.plurals.path_spine_persons_more, rest.size, words),
                style = type.personsFold,
                color = colors.ink2,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ROW_MIN)
                        .rule(colors.rule, above = false)
                        .semantics {
                            stateDescription = state
                            if (expanded) {
                                collapse {
                                    expanded = false
                                    true
                                }
                            } else {
                                expand {
                                    expanded = true
                                    true
                                }
                            }
                        }.clickable(role = Role.Button) { expanded = !expanded }
                        .padding(start = FOLD_INDENT)
                        .wrapContentHeight(Alignment.CenterVertically),
            )
            if (expanded) {
                rest.forEach { person -> PersonRow(person, weekStart) }
            }
        }
    }
}

/** The weekday letters over the dots, decorative (each row's dots say their days in words); the reading day's in ink. */
@Composable
private fun DayLetters(
    letters: List<String>,
    readingDay: () -> Int?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val read = readingDay()
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        letters.forEachIndexed { index, letter ->
            Text(
                text = letter,
                style = type.dayLetter,
                color = readingInk(index == read, colors.ink, colors.ink3),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier.width(DOT),
            )
        }
    }
}

/** A ranked soul joined to its glyph and name. */
private class PersonRowModel(
    val soul: SoulWithGlyph,
    val stat: PersonStat,
)

@Composable
private fun PersonRow(
    person: PersonRowModel,
    weekStart: LocalDate,
) {
    val colors = PebblesTheme.spine.colors
    val locale = LocalConfiguration.current.locales[0]
    val days = person.stat.days
    val (count, named) =
        remember(days, weekStart, locale) {
            WeekWordsBuilder.dotDays(BooleanArray(days.length) { it in days }, weekStart, locale)
        }
    val dotsDescription = pluralStringResource(R.plurals.path_spine_day_dots_a11y, count, count, named)

    MarkRow(
        title = person.soul.name,
        description = "${person.soul.name}, $dotsDescription",
        leading = { SoulMark(person.soul.glyph, colors.ink, Modifier.size(MARK)) },
        trailing = { DaySetMarks(days, DayMode.Row, colors.ink, colors.ink3) },
    )
}
