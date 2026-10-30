package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon

/**
 * Days / Pebbles / Karma counter tiles — ports iOS `ProfileCountersRow.swift`.
 */
@Composable
fun ProfileCountersRow(
    daysPracticed: Int?,
    pebbles: Int?,
    karma: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        DataTile(
            value = daysPracticed,
            icon = PbblsIcon.CALENDAR_DATE,
            label = stringResource(R.string.profile_counter_days),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = pebbles,
            icon = PbblsIcon.PEBBLE_ROUND,
            label = stringResource(R.string.profile_counter_pebbles),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = karma,
            icon = PbblsIcon.KARMA_DOT,
            label = stringResource(R.string.profile_counter_karma),
            modifier = Modifier.weight(1f),
        )
    }
}
