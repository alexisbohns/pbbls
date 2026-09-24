package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesSectionHeader
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.profileCard
import app.pbbls.android.core.model.RippleSummary

/**
 * The profile "Stats" card — ports iOS `ProfileStatsCard.swift`: heading,
 * [RipplesRow], divider, [ProfileCountersRow], all inside the shared
 * `profileCard()` chrome. Pure UI — state arrives from `PathStatsService`
 * via the screen.
 */
@Composable
fun ProfileStatsCard(
    ripple: RippleSummary?,
    assiduity: List<Boolean>?,
    daysPracticed: Int?,
    pebbles: Int?,
    karma: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        modifier = modifier.fillMaxWidth().profileCard(),
    ) {
        PebblesSectionHeader(text = stringResource(R.string.profile_stats_header))
        RipplesRow(ripple = ripple, assiduity = assiduity)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ProfileCountersRow(daysPracticed = daysPracticed, pebbles = pebbles, karma = karma)
    }
}
