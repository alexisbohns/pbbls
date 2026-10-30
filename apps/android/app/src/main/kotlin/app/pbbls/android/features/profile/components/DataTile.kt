package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme

/**
 * Single value/icon/label tile inside the Profile Stats card — ports iOS
 * `DataTile.swift`: large counter number (tabular figures) over a small
 * `primary` icon + `onSurfaceVariant` label. "—" while the value is loading.
 * The icon sits in the label's line, so it draws at INLINE (Android icon
 * migration spec D6; iOS draws it at `tile`).
 */
@Composable
fun DataTile(
    value: Int?,
    icon: PbblsIcon,
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
            PbblsIconImage(
                icon = icon,
                size = PbblsIconSize.INLINE,
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
