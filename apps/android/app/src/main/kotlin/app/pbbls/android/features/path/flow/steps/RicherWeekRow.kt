package app.pbbls.android.features.path.flow.steps

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.features.path.WeekRollBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** One day of the end screen's week. */
data class RicherDay(
    val date: LocalDate,
    val isMarked: Boolean,
)

/** The week around [date], Monday first (as the Path's), with [date] marked. */
fun richerWeek(date: LocalDate): List<RicherDay> {
    val start = WeekRollBuilder.weekStart(date)
    return (0L..6L).map { offset ->
        val day = start.plusDays(offset)
        RicherDay(day, isMarked = day == date)
    }
}

private val DotSize = 6.dp
private val MarkedDotSize = 10.dp

/**
 * The week the new pebble belongs to as seven dots, a larger `primary` one on its day (record-flow D12;
 * the mock's stones are dots on both surfaces). Narrow weekday letters in the active locale.
 */
@Composable
fun RicherWeekRow(
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val spoken =
        DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
            .format(date)
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        richerWeek(date).forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Box(Modifier.height(MarkedDotSize), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(if (day.isMarked) MarkedDotSize else DotSize)
                            .background(if (day.isMarked) colors.primary else colors.outlineVariant, CircleShape),
                    )
                }
            }
        }
    }
}
