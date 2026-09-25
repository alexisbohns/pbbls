package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.achievementFamilyIcon
import app.pbbls.android.core.ui.achievementTitle

/**
 * The Profile achievements shelf (D14) — ports web `AchievementsShelf` and iOS
 * `ProfileAchievementsCard`: the most recently unlocked badges, the total
 * count, and a way into the full grid. It reads the same two queries the grid
 * does — no new endpoint, no new RLS.
 *
 * The shelf shows what you have EARNED; the grid shows the whole ladder. So
 * locked badges never appear here, and a profile with nothing unlocked yet
 * falls back to a plain invitation rather than an empty row.
 *
 * Stateless since #852 — [ProfileViewModel] owns the load (mirroring every
 * other Profile card) and reports [recent]/[unlockedCount]/[hasLoaded]; this
 * used to read the achievements CompositionLocal and fetch for itself via a
 * bare `LaunchedEffect(Unit)`, which never fired again after the first resume.
 */
@Composable
fun ProfileAchievementsCard(
    recent: List<AchievementRecord>,
    unlockedCount: Int,
    hasLoaded: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    // A stock outlined card: the whole card is the target, with its ripple and role (#854).
    OutlinedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(PebblesTheme.spacing.lg),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(PebblesIconToken.LARGE.size),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.achievements_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text =
                        if (hasLoaded && unlockedCount > 0) {
                            stringResource(R.string.achievements_shelf_count, unlockedCount)
                        } else {
                            stringResource(R.string.achievements_card_subtitle)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (recent.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        recent.forEach { record -> ShelfBadge(record) }
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
            )
        }
    }
}

/** One earned badge: icon only, with the localized title as its accessible name. */
@Composable
private fun ShelfBadge(record: AchievementRecord) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
    ) {
        Icon(
            painter = painterResource(achievementFamilyIcon(record.family)),
            contentDescription = achievementTitle(record),
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}
