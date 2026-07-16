package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

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
            iconRes = R.drawable.ic_calendar,
            label = stringResource(R.string.profile_counter_days),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = pebbles,
            iconRes = R.drawable.ic_fossil_shell,
            label = stringResource(R.string.profile_counter_pebbles),
            modifier = Modifier.weight(1f),
        )
        DataTile(
            value = karma,
            iconRes = R.drawable.ic_sparkle,
            label = stringResource(R.string.profile_counter_karma),
            modifier = Modifier.weight(1f),
        )
    }
}
