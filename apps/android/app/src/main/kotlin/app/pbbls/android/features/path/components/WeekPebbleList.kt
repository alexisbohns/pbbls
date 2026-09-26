package app.pbbls.android.features.path.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.WeekRollEntry

/**
 * One pager page: the focused week's pebble list — the `WeekPathView`
 * analog. The iOS reveal cascade and bottom fade mask are skipped in v1 (both
 * choreograph the create button); the empty state keeps the copy and re-adds
 * the create affordance (C) via [onCreatePebble].
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
    onCreatePebble: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (entry.pebbles.isEmpty()) {
        EmptyWeek(onCreate = onCreatePebble, modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.testTag(JourneyTags.PATH_WEEK_LIST),
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
                            .padding(vertical = 8.dp, horizontal = 24.dp)
                            .testTag(JourneyTags.PATH_PEBBLE_ROW),
                    onTap = { onPebbleTap(pebble) },
                    onRequestDelete = { onPebbleDelete(pebble) },
                )
            }
        }
    }
}

@Composable
private fun EmptyWeek(
    onCreate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // iOS: Ysabeau Semibold 20 — titleLarge is the Ysabeau role at that size.
        Text(
            text = stringResource(R.string.path_empty_week_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.path_empty_week_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        NewPebbleButton(
            onTap = onCreate,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        )
    }
}
