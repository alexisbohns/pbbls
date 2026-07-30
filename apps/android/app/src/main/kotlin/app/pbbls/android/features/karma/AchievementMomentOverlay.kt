package app.pbbls.android.features.karma

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.features.shared.achievements.achievementDescription
import app.pbbls.android.features.shared.achievements.achievementTitle
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * The unlock moment (D13): one card per newly unlocked badge, chained in
 * catalog order and advanced by tapping. Drawn in `RootScreen`'s overlay slot
 * above the karma pastille, so a mutation that earns karma AND unlocks a badge
 * shows the flash behind the card — and the card's own "+N karma" line is the
 * badge's, never the pebble's.
 *
 * Dismissal is never blocking: the scrim and the back gesture both skip the
 * rest of the queue.
 */
@Composable
fun AchievementMomentOverlay(
    service: AchievementNotificationService,
    modifier: Modifier = Modifier,
) {
    val card = service.currentCard
    val view = LocalView.current

    // Retain the last card through the exit animation so the panel fades out
    // with its content instead of blanking (the KarmaOverlayHost idiom).
    var lastCard by remember { mutableStateOf<AchievementMomentCard?>(null) }
    if (card != null) lastCard = card

    // One reward buzz per moment, not per card — the queue is a single event.
    LaunchedEffect(card != null) {
        if (card != null) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // The scrim swallows taps so nothing behind the moment reacts while it
        // is up; `indication = null` keeps it from flashing a ripple.
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { service.dismiss() },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shown = card ?: lastCard
            if (shown != null) {
                BackHandler(enabled = card != null) { service.dismiss() }
                MomentCard(
                    card = shown,
                    position = service.index + 1,
                    total = service.cards.size.coerceAtLeast(1),
                    isLast = service.isShowingLastCard,
                    onAdvance = { service.advance() },
                )
            }
        }
    }
}

@Composable
private fun MomentCard(
    card: AchievementMomentCard,
    position: Int,
    total: Int,
    isLast: Boolean,
    onAdvance: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val interaction = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = system.background,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        modifier =
            Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = PebblesTheme.spacing.xl)
                // Taps on the card itself must not reach the dismiss scrim.
                .clickable(interactionSource = interaction, indication = null, onClick = {}),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
            modifier = Modifier.padding(PebblesTheme.spacing.xl),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(accent.primary.copy(alpha = 0.12f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trophy),
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PebblesText(
                    text = stringResource(R.string.achievement_moment_eyebrow),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                )
                PebblesText(
                    text = card.record?.let { achievementTitle(it) } ?: card.slug,
                    style = PebblesTypography.headlineEmphasized,
                    color = system.foreground,
                    textAlign = TextAlign.Center,
                )
                card.record?.let { record ->
                    achievementDescription(record)?.let { description ->
                        PebblesText(
                            text = description,
                            style = PebblesTypography.subhead,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (card.karmaGranted > 0) {
                PebblesText(
                    text = stringResource(R.string.karma_flash_amount, card.karmaGranted),
                    style = PebblesTypography.headline,
                    color = accent.primary,
                )
            }

            if (total > 1) {
                PebblesText(
                    text = stringResource(R.string.achievement_moment_progress, position, total),
                    style = PebblesTypography.subhead,
                    color = system.muted,
                )
            }

            PebblesPrimaryButton(
                text =
                    stringResource(
                        if (isLast) R.string.achievement_moment_done else R.string.achievement_moment_next,
                    ),
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
