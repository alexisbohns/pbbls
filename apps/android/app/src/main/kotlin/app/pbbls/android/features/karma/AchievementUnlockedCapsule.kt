package app.pbbls.android.features.karma

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.shared.achievements.achievementTitle
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * The achievement-unlocked pastille, visual sibling of [KarmaEarnedCapsule]:
 * trophy instead of sparkle, the badge title (or a count phrase for several
 * unlocks) and the karma actually granted when > 0. Localized title
 * resolution happens here — `achievementTitle` is composable (it reads the
 * reference-data locals), which is why the service hands over the catalog row
 * rather than a string.
 */
@Composable
fun AchievementUnlockedCapsule(
    content: AchievementUnlockedContent,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val view = LocalView.current
    val ring = remember { Animatable(1f) }

    val headline =
        if (content.count == 1 && content.single != null) {
            achievementTitle(content.single)
        } else {
            pluralStringResource(R.plurals.achievement_capsule_count, content.count, content.count)
        }
    val a11y =
        buildString {
            append(
                if (content.count == 1 && content.single != null) {
                    stringResource(R.string.achievement_capsule_a11y_single, headline)
                } else {
                    headline
                },
            )
            if (content.karmaTotal > 0) {
                append(", ")
                append(stringResource(R.string.achievement_capsule_a11y_karma, content.karmaTotal))
            }
        }

    LaunchedEffect(content) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
    LaunchedEffect(content) {
        ring.snapTo(1f)
        ring.animateTo(
            targetValue = 0f,
            animationSpec =
                tween(
                    AchievementNotificationService.CAPSULE_DURATION_MS.toInt(),
                    easing = LinearEasing,
                ),
        )
    }

    Surface(
        shape = CircleShape,
        color = system.background,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier =
            modifier
                .semantics { contentDescription = a11y }
                .clickable(onClick = onTap)
                .drawWithContent {
                    drawContent()
                    drawKarmaRing(ring.value, accent.primary)
                },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(16.dp),
            )
            PebblesText(
                text = headline,
                style = PebblesTypography.buttonLabel,
                color = system.foreground,
                maxLines = 1,
            )
            if (content.karmaTotal > 0) {
                PebblesText(
                    text = stringResource(R.string.karma_flash_amount, content.karmaTotal),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        }
    }
}
