package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.pbbls.android.theme.PebblesIcon
import app.pbbls.android.theme.PebblesIconToken
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Single value/icon/label tile inside the Profile Stats card — ports iOS
 * `DataTile.swift`: large counter number (tabular figures) over a small
 * accent icon + secondary label. "—" while the value is loading.
 */
@Composable
fun DataTile(
    value: Int?,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
        modifier = modifier,
    ) {
        PebblesText(
            text = value?.toString() ?: "—",
            // The .monospacedDigit() analog — tabular figures via font features.
            style = PebblesTypography.counterLg.copy(fontFeatureSettings = "tnum"),
            color = system.foreground,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PebblesIcon(
                painter = painterResource(iconRes),
                token = PebblesIconToken.SMALL,
                contentDescription = null,
                tint = PebblesTheme.colors.accent.primary,
            )
            PebblesText(
                text = label,
                style = PebblesTypography.subhead,
                color = system.secondary,
            )
        }
    }
}
