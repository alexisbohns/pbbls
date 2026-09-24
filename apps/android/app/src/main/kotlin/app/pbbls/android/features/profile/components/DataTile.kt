package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.pbbls.android.core.designsystem.PebblesIcon
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesTheme

/**
 * Single value/icon/label tile inside the Profile Stats card — ports iOS
 * `DataTile.swift`: large counter number (tabular figures) over a small
 * `primary` icon + `onSurfaceVariant` label. "—" while the value is loading.
 */
@Composable
fun DataTile(
    value: Int?,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        modifier = modifier,
    ) {
        Text(
            text = value?.toString() ?: "—",
            // The .monospacedDigit() analog — tabular figures via font features.
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PebblesIcon(
                painter = painterResource(iconRes),
                token = PebblesIconToken.SMALL,
                contentDescription = null,
                tint = colors.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
