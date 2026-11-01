package app.pbbls.android.features.home.wraps

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.features.home.HomeSection
import app.pbbls.android.features.home.HomeWraps
import java.time.YearMonth

/** Under the head, to the cards (the mock's `.sh` margin). */
private val HEAD_GAP = 18.dp

/**
 * Room the row keeps around its cards for their shadows (PebblesCard's 22 dp blur, 8 dp down), then gives back
 * to the page: a lazy row clips to its bounds, and the mock lets the shadows run over the gaps instead.
 */
private val SHADOW_ABOVE = 14.dp
private val SHADOW_BELOW = 30.dp

/**
 * Home's monthly wraps (#1383 D12; iOS `HomeWrapsRow`): "Monthly wraps" with the count in `sectionFigure`, then
 * a row of [HomeWrapCard]s, newest first, 12 dp apart, snapping to a card, running edge to edge across the
 * page's [gutter]. No month yet: the [WrapPromiseCard] for [liveMonth]. A failed read says so under the row
 * with Try again ([onRetry]), keeping the months it had. [onOpen] gets the month tapped.
 */
@Composable
internal fun HomeWrapsSection(
    wraps: HomeSection<HomeWraps>,
    liveMonth: YearMonth,
    gutter: Dp,
    textInset: Dp,
    onOpen: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = (wraps as? HomeSection.Ready)?.value
    val months = shown?.months.orEmpty()
    Column(modifier) {
        SectionHead(
            title = stringResource(R.string.home_wraps_title),
            figure = if (months.isEmpty()) null else AnnotatedString(months.size.toString()),
            modifier = Modifier.padding(horizontal = textInset),
        )
        if (shown != null) {
            Spacer(Modifier.height(HEAD_GAP))
            val gap = PebblesTheme.spacing.md
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = SHADOW_ABOVE, bottom = SHADOW_BELOW),
                modifier = Modifier.bleed(gutter, SHADOW_ABOVE, SHADOW_BELOW),
            ) {
                if (months.isEmpty()) {
                    item(key = "promise") { WrapPromiseCard(liveMonth) }
                } else {
                    items(months, key = { it.month.toString() }) { month ->
                        HomeWrapCard(month = month, onOpen = { onOpen(month.month) })
                    }
                }
            }
        }
        if (shown == null || shown.stale) {
            WrapsError(onRetry, Modifier.padding(start = textInset, top = PebblesTheme.spacing.sm))
        }
    }
}

/** "Couldn't load your months." and Try again (iOS: callout, `onSurfaceVariant`, on one baseline). */
@Composable
private fun WrapsError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_wraps_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.home_wraps_retry)) }
    }
}

/**
 * Widens the row by [sides] on each side (it runs under the page's gutters) and lets it overhang [above] and
 * [below] while taking only its cards' height in the page.
 */
private fun Modifier.bleed(
    sides: Dp,
    above: Dp,
    below: Dp,
): Modifier =
    layout { measurable, constraints ->
        val side = sides.roundToPx()
        val top = above.roundToPx()
        val bottom = below.roundToPx()
        val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * side else constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
        val height = (placeable.height - top - bottom).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        layout(width, height) { placeable.place(-side, -top) }
    }
