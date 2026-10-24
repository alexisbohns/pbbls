package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.MarkRow
import app.pbbls.android.core.ui.SoulMark
import app.pbbls.android.core.ui.marks.CairnMark
import app.pbbls.android.core.ui.marks.CairnState
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy
import java.time.LocalDate

private val GLYPH = 14.dp
private const val GLYPHS = 5

/**
 * The month by week (#1207, D6): each ISO week cut to the month's days, its cairn, its range and
 * one line, and its souls; a tap focuses that week in the strip.
 */
@Composable
internal fun WeeksSummary(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onWeek: (LocalDate) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val weeks = wrap.weeks.orEmpty().filter { it.pebbles > 0 }
    if (weeks.isEmpty()) return
    Column {
        MonthSectionHead(copy.weeksHeader(weeks.size), copy.weeksHint(wrap.month))
        Column(Modifier.padding(horizontal = SECTION_PAD_X)) {
            weeks.forEach { week ->
                val range = copy.range(wrap.month, week.from, week.to)
                val line = copy.line(week.pebbles, week.soulIds.size, live = false, hasMoments = true)
                val souls = week.soulIds.mapNotNull(lookups.soul)
                MarkRow(
                    title = line,
                    titleStyle = type.mouthLine,
                    subtitle = range,
                    description = "$range, $line",
                    leading = { CairnMark(week.days, CairnState.SETTLED, colors.ink2) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            souls.take(GLYPHS).forEach { SoulMark(it.glyph, colors.ink2, Modifier.size(GLYPH)) }
                        }
                    },
                    onClick = { onWeek(week.weekStart) },
                )
            }
        }
    }
}
