package app.pbbls.android.features.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.profileCard

/**
 * The Profile "Lab" card — ports iOS `ProfileLabCard.swift`: lightbulb in
 * `primary`, "Lab" + "News & community", `onSurfaceVariant` chevron, `profileCard` chrome.
 * Un-hidden in M44 (design D11) — the last deliberately-omitted M41 tile —
 * now that the Lab route exists; pushes the Lab.
 */
@Composable
fun ProfileLabCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onOpen)
                .profileCard(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lightbulb),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(PebblesIconToken.LARGE.size),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.lab_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.lab_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
        )
    }
}
