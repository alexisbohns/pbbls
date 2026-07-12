package app.pbbls.android.features.path.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * One pager page: the focused week's pebble list — the `WeekPathView`
 * analog. The iOS reveal cascade and bottom fade mask are skipped in v1
 * (both choreograph the create button, which doesn't exist on the read-only
 * surface); the empty state keeps the copy but drops the create button for
 * the same reason.
 *
 * [paletteFor] keeps the list previewable — screenshot tests pass a fixture
 * lookup instead of a live palette service.
 */
@Composable
fun WeekPebbleList(
    entry: WeekRollEntry,
    paletteFor: (Pebble) -> EmotionPalette?,
    onPebbleTap: (Pebble) -> Unit = {},
    onPebbleDelete: (Pebble) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (entry.pebbles.isEmpty()) {
        EmptyWeek(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            itemsIndexed(entry.pebbles, key = { _, pebble -> pebble.id }) { index, pebble ->
                PathPebbleRow(
                    pebble = pebble,
                    positionIndex = index,
                    palette = paletteFor(pebble),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 24.dp),
                    onTap = { onPebbleTap(pebble) },
                    onRequestDelete = { onPebbleDelete(pebble) },
                )
            }
        }
    }
}

@Composable
private fun EmptyWeek(modifier: Modifier = Modifier) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // iOS: Ysabeau Semibold 20 — buttonLabel is the Ysabeau token.
        PebblesText(
            text = stringResource(R.string.path_empty_week_title),
            style = PebblesTypography.buttonLabel.copy(fontSize = 20.sp),
            color = system.foreground,
        )
        PebblesText(
            text = stringResource(R.string.path_empty_week_subtitle),
            style = PebblesTypography.meta,
            color = system.secondary,
        )
    }
}
