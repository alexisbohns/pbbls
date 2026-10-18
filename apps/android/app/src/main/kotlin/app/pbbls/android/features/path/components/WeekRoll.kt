package app.pbbls.android.features.path.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.features.path.WeekRollBuilder
import java.time.LocalDate

private val CELL_WIDTH = 56.dp
private val CELL_HEIGHT = 44.dp

/** The focused numeral's underline: the web's `decoration-1 underline-offset-[6px]`, measured from the text box here. */
private val UNDERLINE = 1.dp
private val UNDERLINE_OFFSET = 2.dp

/**
 * The horizontal week strip above the pager — the `WeekRollView` analog, in the
 * Spine's chrome (#1122, spec §Chrome D5; the web's `WeekRoll.tsx`). Each cell is
 * the ISO week number in small caps, ink-2, the focused one in ink and underlined.
 * Tapping a cell drives [onFocusChange]; the strip auto-centers the focused cell.
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
    val spine = PebblesTheme.spine
    val ink = if (isFocused) spine.colors.ink else spine.colors.ink2
    val weekNumber = WeekRollBuilder.isoWeekNumber(entry.weekStart)
    val cellLabel = stringResource(R.string.path_week_cell_a11y, weekNumber, entry.moments.size)

    Box(
        modifier =
            Modifier
                .size(CELL_WIDTH, CELL_HEIGHT)
                .clickable(onClick = onTap)
                .semantics { contentDescription = cellLabel },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekNumber.toString(),
            style = spine.type.rollNumeral,
            color = ink,
            modifier =
                Modifier.drawBehind {
                    // Under the numeral, not the cell: the line is as wide as the figures.
                    if (isFocused) {
                        val y = size.height + UNDERLINE_OFFSET.toPx() - UNDERLINE.toPx()
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), UNDERLINE.toPx())
                    }
                },
        )
    }
}
