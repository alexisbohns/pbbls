package app.pbbls.android.features.path.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.LocalDate

private val CELL_WIDTH = 72.dp

/**
 * The horizontal week strip above the header — the `WeekRollView` analog.
 * Each cell is a static cairn (v1 — the iOS Rive state machine is a
 * fast-follow) + the ISO week number, accent-tinted when focused. Tapping a
 * cell drives [onFocusChange]; the strip auto-centers the focused cell.
 */
@Composable
fun WeekRoll(
    entries: List<WeekRollEntry>,
    focusedWeekStart: LocalDate,
    onFocusChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier) {
        // Side padding of (viewport − cell)/2 makes scrollToItem(index) land
        // the cell dead-center — the iOS contentMargins/scrollTarget analog.
        val sidePadding = (maxWidth - CELL_WIDTH) / 2

        LaunchedEffect(focusedWeekStart, entries.size) {
            val index = entries.indexOfFirst { it.weekStart == focusedWeekStart }
            if (index >= 0) listState.animateScrollToItem(index)
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(entries, key = { it.weekStart.toEpochDay() }) { entry ->
                WeekRollCell(
                    entry = entry,
                    isFocused = entry.weekStart == focusedWeekStart,
                    onTap = { onFocusChange(entry.weekStart) },
                )
            }
        }
    }
}

@Composable
private fun WeekRollCell(
    entry: WeekRollEntry,
    isFocused: Boolean,
    onTap: () -> Unit,
) {
    val accent = PebblesTheme.colors.accent
    val system = PebblesTheme.colors.system
    val tint = if (isFocused) accent.primary else system.secondary
    val weekNumber = WeekRollBuilder.isoWeekNumber(entry.weekStart)
    val cellLabel = stringResource(R.string.path_week_cell_a11y, weekNumber, entry.pebbles.size)

    Column(
        modifier =
            Modifier
                .width(CELL_WIDTH)
                .height(96.dp)
                .clickable(onClick = onTap)
                .semantics { contentDescription = cellLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.cairn_static),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(56.dp),
        )
        // iOS renders the week number in Ysabeau Semibold 13 — buttonLabel is
        // the Ysabeau token; only the size differs from its 17sp default.
        PebblesText(
            text = weekNumber.toString(),
            style = PebblesTypography.buttonLabel.copy(fontSize = 13.sp),
            color = tint,
        )
    }
}
