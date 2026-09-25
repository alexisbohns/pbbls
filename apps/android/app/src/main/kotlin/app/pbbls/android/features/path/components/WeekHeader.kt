package app.pbbls.android.features.path.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.WeekHeaderFormatting
import app.pbbls.android.features.path.WeekRollBuilder
import java.time.LocalDate

/**
 * The "MAY 4 · MAY 10" pill above the path body — the `WeekHeaderView`
 * analog: focused week's date range (year suffix only when the week-based
 * year differs from today's), chevrons stepping through [entries], disabled
 * at the edges. The chevrons are 48 dp `IconButton`s with auto-mirrored
 * vectors, so "previous" points to the start edge in RTL.
 */
@Composable
fun WeekHeader(
    entries: List<WeekRollEntry>,
    focusedWeekStart: LocalDate,
    today: LocalDate,
    onFocusChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val label =
        WeekHeaderFormatting
            .formatRange(focusedWeekStart, today, locale)
            .uppercase(locale)
    val previous = WeekRollBuilder.previous(focusedWeekStart, entries)
    val next = WeekRollBuilder.next(focusedWeekStart, entries)

    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.large)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronButton(
            pointsLeft = true,
            targetWeekStart = previous?.weekStart,
            a11yLabel = stringResource(R.string.path_week_previous),
            onFocusChange = onFocusChange,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        ChevronButton(
            pointsLeft = false,
            targetWeekStart = next?.weekStart,
            a11yLabel = stringResource(R.string.path_week_next),
            onFocusChange = onFocusChange,
        )
    }
}

@Composable
private fun ChevronButton(
    pointsLeft: Boolean,
    targetWeekStart: LocalDate?,
    a11yLabel: String,
    onFocusChange: (LocalDate) -> Unit,
) {
    IconButton(
        onClick = { targetWeekStart?.let(onFocusChange) },
        enabled = targetWeekStart != null,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            painter = painterResource(if (pointsLeft) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right),
            contentDescription = a11yLabel,
        )
    }
}
