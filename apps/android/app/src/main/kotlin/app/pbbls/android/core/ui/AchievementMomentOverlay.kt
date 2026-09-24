package app.pbbls.android.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementMoment
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rememberReduceMotion

/**
 * The unlock moment (D13): one card per newly unlocked badge, chained in
 * catalog order and advanced by tapping. Drawn in `RootScreen`'s overlay slot
 * above the karma pastille, so a mutation that earns karma AND unlocks a badge
 * shows the flash behind the card — and the card's own "+N karma" line is the
 * badge's, never the pebble's.
 *
 * Takes state, not the service (#852) — [moment] is
 * [AchievementNotificationService.moment], surfaced through `RootViewModel`'s
 * `RootUiState` the same way [KarmaOverlayHost] took `karmaFlash` since #849.
 * It is a queue position rather than a single card because the queue is real:
 * three badges from one mutation show three cards with "1 of 3" progress, and
 * flattening that to `(card, onDismiss)` would silently drop the progress UI.
 *
 * Dismissal is never blocking: the scrim and the back gesture both skip the
 * rest of the queue.
 */
@Composable
fun AchievementMomentOverlay(
    moment: AchievementMoment?,
    onAdvance: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    // Retain the last moment through the exit animation so the panel fades out
    // with its content instead of blanking (the KarmaOverlayHost idiom).
    var lastMoment by remember { mutableStateOf<AchievementMoment?>(null) }
    if (moment != null) lastMoment = moment

    // One reward buzz per moment, not per card — the queue is a single event.
    LaunchedEffect(moment != null) {
        if (moment != null) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = moment != null,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(),
        modifier = modifier,
    ) {
        // The scrim swallows taps so nothing behind the moment reacts while it
        // is up; `indication = null` keeps it from flashing a ripple.
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shown = moment ?: lastMoment
            if (shown != null) {
                // NavigationBackHandler (not the legacy BackHandler, #852): still a
                // handler rather than a nav entry, since this overlay is drawn above
                // `NavDisplay`, not on its back stack.
                val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
                NavigationBackHandler(state = backState, isBackEnabled = moment != null) {
                    onDismiss()
                }
                MomentCard(
                    card = shown.card,
                    position = shown.position,
                    total = shown.total,
                    isLast = shown.isLast,
                    onAdvance = onAdvance,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MomentCard(
    card: AchievementMomentCard,
    position: Int,
    total: Int,
    isLast: Boolean,
    onAdvance: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val interaction = remember { MutableInteractionSource() }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        // Lift from the container ladder, not a `surfaceTint` wash (#853).
        color = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
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
                        .background(colors.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trophy),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.achievement_moment_eyebrow),
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = card.record?.let { achievementTitle(it) } ?: card.slug,
                    style = type.titleMediumEmphasized,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                card.record?.let { record ->
                    achievementDescription(record)?.let { description ->
                        Text(
                            text = description,
                            style = type.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (card.karmaGranted > 0) {
                Text(
                    text = stringResource(R.string.karma_flash_amount, card.karmaGranted),
                    style = type.titleMedium,
                    color = colors.primary,
                )
            }

            if (total > 1) {
                // Informative, not disabled: `onSurfaceVariant` keeps it legible
                // where the 38% disabled alpha would fail contrast (#853).
                Text(
                    text = stringResource(R.string.achievement_moment_progress, position, total),
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
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
