package app.pbbls.android.features.path.spine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.ui.render.stone.StoneSeal
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_DAY_COL
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_HEADER_GAP
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_NAME_PAD_END
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_NAME_PAD_Y
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_ROW_MIN
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_ROW_PAD_Y
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_SEAL_COL
import app.pbbls.android.features.path.spine.SpineLayout.CONTENTS_TOP
import app.pbbls.android.features.path.spine.SpineLayout.READING_DOT
import app.pbbls.android.features.path.spine.SpineLayout.READING_DOT_OFFSET
import app.pbbls.android.features.path.spine.SpineLayout.SEAL_WIDTH
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X
import app.pbbls.android.features.path.spine.logic.EntryTypes
import app.pbbls.android.features.path.spine.logic.SpineDay
import app.pbbls.android.features.path.spine.logic.SpineWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * The week's contents (#1122, spec §The opening; the web's `WeekContents.tsx`): one row
 * per moment in reading order, each a button that goes to its stone and presses it
 * without opening it (spec D8). The stone is printed here as a flat seal, never lit
 * (D7); a moment that is not a pebble yet prints as a blank medium seal. The weekday
 * heads the first row of each day, with a rule above it; the day being read turns ink
 * and gets the dot in the margin. Nothing when the week holds no moment.
 *
 * @param paletteFor the pebble's emotion palette; null for a blank seal.
 * @param readingDay 0 = Monday.
 * @param onGo called with the moment whose row was pressed.
 */
@Composable
fun WeekContents(
    week: SpineWeek,
    paletteFor: (PathMoment) -> EmotionPalette?,
    readingDay: Int?,
    onGo: (PathMoment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (week.days.all { it.moments.isEmpty() }) return
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type

    Column(modifier.padding(horizontal = VERSO_PAD_X).padding(top = CONTENTS_TOP)) {
        Text(
            text = stringResource(R.string.path_spine_contents),
            style = type.sectionLabel,
            color = colors.ink2,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.padding(top = CONTENTS_HEADER_GAP)) {
            var firstOfList = true
            week.days.forEach { day ->
                day.moments.forEachIndexed { index, moment ->
                    ContentsRow(
                        moment = moment,
                        day = day,
                        palette = paletteFor(moment),
                        first = index == 0,
                        ruleAbove = index == 0 && !firstOfList,
                        here = day.index == readingDay,
                        onGo = onGo,
                    )
                    firstOfList = false
                }
            }
        }
    }
}

@Composable
private fun ContentsRow(
    moment: PathMoment,
    day: SpineDay,
    palette: EmotionPalette?,
    first: Boolean,
    ruleAbove: Boolean,
    here: Boolean,
    onGo: (PathMoment) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val valence = moment.valence
    val nameType = remember(valence) { EntryTypes.contentsName(valence.intensity, valence.polarity) }
    val name = moment.name?.takeIf { it.isNotBlank() }
    val untitled = stringResource(R.string.path_spine_untitled)
    val time =
        remember(moment.happenedAt, locale) {
            moment.happenedAt
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        }
    val weekday = remember(day.date, locale) { shortWeekday(day.date.dayOfWeek, locale) }
    // "Monday 5", as the persons' dots name a day.
    val dayLong = remember(day.date, locale) { "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${day.date.dayOfMonth}" }
    val description = "${name ?: untitled}, $dayLong, $time"
    val dayInk = readingInk(here, colors.ink, colors.ink2)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CONTENTS_ROW_MIN)
                .drawBehind {
                    if (ruleAbove) {
                        val y = SpineLayout.RULE.toPx() / 2
                        drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), SpineLayout.RULE.toPx())
                    }
                    // The reading dot, in the margin beside the day's first row.
                    if (here && first) {
                        val radius = READING_DOT.toPx() / 2
                        drawCircle(colors.ink, radius, Offset(radius - READING_DOT_OFFSET.toPx(), size.height / 2))
                    }
                }.clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onGo(moment)
                        true
                    }
                }.clickable(role = Role.Button) { onGo(moment) }
                .padding(vertical = CONTENTS_ROW_PAD_Y),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (first) weekday else "",
            style = type.contentsDay,
            color = dayInk,
            maxLines = 1,
            modifier = Modifier.width(CONTENTS_DAY_COL),
        )
        Box(Modifier.width(CONTENTS_SEAL_COL), contentAlignment = Alignment.CenterStart) {
            StoneSeal(
                valence = valence,
                palette = palette,
                glyph = moment.pebble?.glyph,
                knockout = colors.ground,
                modifier = Modifier.width(SEAL_WIDTH.getValue(valence.sizeGroup)),
            )
        }
        Text(
            text = name ?: untitled,
            style = type.contentsName(nameType.fontWeight, nameType.italic || name == null),
            color = if (name != null) colors.ink else colors.ink3,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = CONTENTS_NAME_PAD_Y, bottom = CONTENTS_NAME_PAD_Y, end = CONTENTS_NAME_PAD_END),
        )
        Text(text = time, style = type.contentsTime, color = colors.ink3)
    }
}
