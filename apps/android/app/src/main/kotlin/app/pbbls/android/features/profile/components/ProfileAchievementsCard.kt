package app.pbbls.android.features.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesIconToken
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.profileCard

/**
 * The Profile "Achievements" card (M48) — ports iOS `ProfileAchievementsCard`:
 * trophy in accent, title + subtitle, muted chevron, `profileCard` chrome.
 * Pushes the achievements grid; the showcase shelf is the D14 satellite, not
 * this card.
 */
@Composable
fun ProfileAchievementsCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
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
                text = stringResource(R.string.achievements_card_subtitle),
                style = PebblesTypography.subhead,
                color = system.secondary,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = system.muted,
            modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
        )
    }
}
