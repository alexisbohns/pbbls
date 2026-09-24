package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.core.ui.RippleBadge

/**
 * Ripples strip inside the Stats card — ports iOS `RipplesRow.swift`: badge,
 * "Ripples Level N" headline with the progress copy ("X more pebbles to level
 * Y" / "Max level reached" / "Loading…"), and the 28-day [AssiduityGrid].
 */
@Composable
fun RipplesRow(
    ripple: RippleSummary?,
    assiduity: List<Boolean>?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progressCopy =
        when {
            ripple == null -> stringResource(R.string.profile_ripples_loading)
            ripple.pebblesToNextLevel != null && ripple.nextLevel != null ->
                pluralStringResource(
                    R.plurals.profile_ripples_progress,
                    ripple.pebblesToNextLevel ?: 0,
                    ripple.pebblesToNextLevel ?: 0,
                    ripple.nextLevel ?: 0,
                )
            else -> stringResource(R.string.profile_ripples_max)
        }

    Row(
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        RippleBadge(
            level = ripple?.rippleLevel ?: 0,
            activeToday = ripple?.activeToday ?: false,
        )
        Column {
            Text(
                text = stringResource(R.string.profile_ripples_level, ripple?.rippleLevel ?: 0),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = progressCopy,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp).weight(1f))
        AssiduityGrid(data = assiduity ?: List(28) { false })
    }
}
