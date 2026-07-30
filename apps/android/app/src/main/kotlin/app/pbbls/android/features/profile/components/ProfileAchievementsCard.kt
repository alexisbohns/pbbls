package app.pbbls.android.features.profile.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.shared.achievements.achievementFamilyIcon
import app.pbbls.android.features.shared.achievements.achievementTitle
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.theme.PebblesIconToken
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.profileCard

private const val TAG = "profile-achievements"

/** How many recent badges the shelf shows before "view all" takes over. */
private const val SHELF_SIZE = 6

/**
 * The Profile achievements shelf (D14) — ports web `AchievementsShelf` and iOS
 * `ProfileAchievementsCard`: the most recently unlocked badges, the total
 * count, and a way into the full grid. It reads the same two queries the grid
 * does — no new endpoint, no new RLS.
 *
 * The shelf shows what you have EARNED; the grid shows the whole ladder. So
 * locked badges never appear here, and a profile with nothing unlocked yet
 * falls back to a plain invitation rather than an empty row.
 */
@Composable
fun ProfileAchievementsCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val achievements = LocalAchievementsService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    var recent by remember { mutableStateOf<List<AchievementRecord>>(emptyList()) }
    var unlockedCount by remember { mutableIntStateOf(0) }
    var hasLoaded by remember { mutableStateOf(false) }

    // Reads only — the shelf never fires an evaluation. The grid's screen-open
    // call is the retroactive grant; doing it here too would double the work on
    // every profile visit.
    LaunchedEffect(Unit) {
        try {
            val catalog = achievements.loadCatalog().associateBy { it.id }
            val earned =
                achievements
                    .loadUnlocks()
                    .sortedByDescending { it.unlockedAt }
                    .mapNotNull { catalog[it.achievementId] }
            unlockedCount = earned.size
            recent = earned.take(SHELF_SIZE)
        } catch (e: Exception) {
            // A failed shelf is not worth an error state on the profile: the
            // card still navigates, and the grid surfaces real failures.
            Log.e(TAG, "achievements shelf fetch failed", e)
        } finally {
            hasLoaded = true
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PebblesTheme.spacing.lg))
                .clickable(onClick = onOpen)
                .profileCard(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_trophy),
            contentDescription = null,
            tint = accent.primary,
            modifier = Modifier.size(PebblesIconToken.LARGE.size),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PebblesText(
                text = stringResource(R.string.achievements_title),
                style = PebblesTypography.headline,
                color = system.foreground,
            )
            PebblesText(
                text =
                    if (hasLoaded && unlockedCount > 0) {
                        stringResource(R.string.achievements_shelf_count, unlockedCount)
                    } else {
                        stringResource(R.string.achievements_card_subtitle)
                    },
                style = PebblesTypography.subhead,
                color = system.secondary,
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
            tint = system.muted,
            modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
        )
    }
}

/** One earned badge: icon only, with the localized title as its accessible name. */
@Composable
private fun ShelfBadge(record: AchievementRecord) {
    val accent = PebblesTheme.colors.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.12f)),
    ) {
        Icon(
            painter = painterResource(achievementFamilyIcon(record.family)),
            contentDescription = achievementTitle(record),
            tint = accent.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
