package app.pbbls.android.features.path.spine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.features.path.spine.SpineLayout.DOT
import app.pbbls.android.features.path.spine.SpineLayout.DOT_GAP
import app.pbbls.android.features.path.spine.SpineLayout.DOT_RING
import app.pbbls.android.features.path.spine.SpineLayout.FOLD_INDENT
import app.pbbls.android.features.path.spine.SpineLayout.PERSONS_HEADER_GAP
import app.pbbls.android.features.path.spine.SpineLayout.PERSONS_TOP
import app.pbbls.android.features.path.spine.SpineLayout.PERSON_COL_GAP
import app.pbbls.android.features.path.spine.SpineLayout.PERSON_GLYPH
import app.pbbls.android.features.path.spine.SpineLayout.PERSON_GLYPH_COL
import app.pbbls.android.features.path.spine.SpineLayout.PERSON_ROW_MIN
import app.pbbls.android.features.path.spine.SpineLayout.RULE
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X
import app.pbbls.android.features.path.spine.logic.PersonOfWeek
import app.pbbls.android.features.path.spine.logic.PersonsOfWeek
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import java.time.LocalDate

private const val DAYS_IN_WEEK = 7

/** An empty dot's ring, in ink-3 at the web's `opacity-70`. */
private const val EMPTY_DOT_ALPHA = 0.7f

/**
 * Who was there (#1122, spec §The opening; the web's `PersonsOfWeek.tsx`): each soul of
 * the week with its glyph, its name in small caps and seven dots for the days it appears
 * on, under a header of weekday letters right-aligned over the dots. Eight rows, then the
 * rest behind "and *N* others", so a fourteen-person picnic does not push the contents off
 * the first screen. Nothing when the week names nobody.
 *
 * @param persons in `PersonsOfWeek.of` order.
 * @param soulsById the glyphs; a soul missing here keeps the dashed placeholder.
 * @param readingDay 0 = Monday: its letter in the header turns ink.
 */
@Composable
fun PersonsOfWeek(
    persons: List<PersonOfWeek>,
    weekStart: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    readingDay: Int?,
    modifier: Modifier = Modifier,
) {
    if (persons.isEmpty()) return
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val (shown, rest) = remember(persons) { PersonsOfWeek.split(persons) }
    val letters = remember(weekStart, locale) { WeekWordsBuilder.weekdayLetters(weekStart, locale) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = VERSO_PAD_X).padding(top = PERSONS_TOP)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.path_spine_persons),
                style = type.sectionLabel,
                color = colors.ink2,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            // The letters are decorative: each row's dots say their days in words.
            Row(
                modifier = Modifier.alignByBaseline().clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
            ) {
                letters.forEachIndexed { index, letter ->
                    Text(
                        text = letter,
                        style = type.dayLetter,
                        color = readingInk(index == readingDay, colors.ink, colors.ink3),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.width(DOT),
                    )
                }
            }
        }
        Column(Modifier.padding(top = PERSONS_HEADER_GAP).rule(colors.rule, above = true)) {
            shown.forEach { person -> PersonRow(person, weekStart, soulsById) }
        }
        if (rest.isNotEmpty()) {
            val words = remember(rest.size, locale) { NumberWords.cardinal(rest.size, locale, NumberWords.Gender.FEMININE) }
            Text(
                text = pluralStringResource(R.plurals.path_spine_persons_more, rest.size, words),
                style = type.personsFold,
                color = colors.ink2,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PERSON_ROW_MIN)
                        .rule(colors.rule, above = false)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .padding(start = FOLD_INDENT)
                        .wrapContentHeight(Alignment.CenterVertically),
            )
            if (expanded) {
                rest.forEach { person -> PersonRow(person, weekStart, soulsById) }
            }
        }
    }
}

@Composable
private fun PersonRow(
    person: PersonOfWeek,
    weekStart: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val (count, days) = remember(person, weekStart, locale) { WeekWordsBuilder.dotDays(person.days, weekStart, locale) }
    val dotsDescription = pluralStringResource(R.plurals.path_spine_day_dots_a11y, count, count, days)
    val description = "${person.soul.name}, $dotsDescription"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PERSON_ROW_MIN)
                .rule(colors.rule, above = false)
                .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PERSON_COL_GAP),
    ) {
        Box(Modifier.width(PERSON_GLYPH_COL)) {
            SoulMark(soulsById[person.soul.id]?.glyph, colors.ink, Modifier.size(PERSON_GLYPH))
        }
        Text(
            text = person.soul.name,
            style = type.personName,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        DayDots(person.days, colors.ink, colors.ink3)
    }
}

/** Seven dots, Monday first: filled in ink on a day present, an inset ring otherwise. */
@Composable
private fun DayDots(
    days: BooleanArray,
    ink: Color,
    ring: Color,
) {
    Canvas(Modifier.size(width = DOT * DAYS_IN_WEEK + DOT_GAP * (DAYS_IN_WEEK - 1), height = DOT)) {
        val dot = DOT.toPx()
        val radius = dot / 2
        val step = dot + DOT_GAP.toPx()
        val ringWidth = DOT_RING.toPx()
        days.forEachIndexed { index, on ->
            val centre = Offset(index * step + radius, radius)
            if (on) {
                drawCircle(ink, radius, centre)
            } else {
                drawCircle(ring, radius - ringWidth / 2, centre, alpha = EMPTY_DOT_ALPHA, style = Stroke(ringWidth))
            }
        }
    }
}

/** A section's hairline rule along the top or the bottom edge. */
internal fun Modifier.rule(
    color: Color,
    above: Boolean,
): Modifier =
    drawBehind {
        val y = if (above) RULE.toPx() / 2 else size.height - RULE.toPx() / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), RULE.toPx())
    }
