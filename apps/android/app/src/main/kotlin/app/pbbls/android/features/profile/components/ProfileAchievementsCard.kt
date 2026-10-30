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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.achievementFamilyIcon
import app.pbbls.android.core.ui.achievementTitle

/**
 * The Profile achievements shelf (D14, D18; iOS `ProfileAchievementsCard`): the most recent badges
 * and their count, as `get_achievements` serves them (`shelf`, `shelf_count`), and a way into the
 * page. The server picks, orders and counts; [ProfileViewModel] titles them from the catalog. With
 * no [count] served (nothing earned yet, or nothing read), it falls back to a plain invitation.
 *
 * Stateless since #852: [ProfileViewModel] owns the load, mirroring every other Profile card.
 */
@Composable
fun ProfileAchievementsCard(
    badges: List<AchievementRecord>,
    count: Int?,
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
            PbblsIconImage(
                icon = PbblsIcon.PEBBLE_GLOW_MD,
                size = PbblsIconSize.TILE,
                contentDescription = null,
                tint = colors.primary,
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
                        count?.let { pluralStringResource(R.plurals.achievements_shelf_count, it, it) }
                            ?: stringResource(R.string.achievements_card_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (badges.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        badges.forEach { record -> ShelfBadge(record) }
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
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
        // A fixed slot (spec D6): the 32dp disc does not grow with the font
        // scale, so the mark keeps a 16dp side instead of following INLINE.
        PbblsIconImage(
            icon = achievementFamilyIcon(record.family),
            // A served row with no slug and no family has no title of its own.
            contentDescription = achievementTitle(record).ifBlank { stringResource(R.string.achievements_title) },
            size = 16.dp,
            tint = colors.onPrimaryContainer,
        )
    }
}
