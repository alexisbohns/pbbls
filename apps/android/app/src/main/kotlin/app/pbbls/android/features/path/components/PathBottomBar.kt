package app.pbbls.android.features.path.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.shared.ripples.RippleBadge
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Bottom stats bar for the Path screen — ports iOS `PathBottomBar.swift`.
 *
 * Left: profile button. Right: karma stat (sparkle + number + caption)
 * followed by the Ripples badge. Karma and badge are independent tap targets
 * so a future Ripples explainer sheet can wire in without restructuring; all
 * three currently route to [onProfile] (a stub until the Profile screen lands
 * in sub-project C).
 */
@Composable
fun PathBottomBar(
    karma: Int?,
    ripple: RippleSummary?,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val system = PebblesTheme.colors.system
    // iOS tints the number accent in dark mode, foreground in light.
    val numberColor = if (isSystemInDarkTheme()) accent.primary else system.foreground
    val karmaText = karma?.toString() ?: "—"
    val karmaLabel = stringResource(R.string.path_karma_a11y, karmaText)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onProfile) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = stringResource(R.string.path_profile),
                tint = accent.primary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clickable(onClick = onProfile)
                    .padding(vertical = 4.dp)
                    // One combined target for TalkBack — the iOS accessibilityLabel analog.
                    .clearAndSetSemantics { contentDescription = karmaLabel },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(16.dp),
            )
            Column {
                PebblesText(
                    text = karmaText,
                    style = PebblesTypography.buttonLabel,
                    color = numberColor,
                )
                PebblesText(
                    text = stringResource(R.string.path_karma_caption),
                    style = PebblesTypography.captionEmphasized,
                    color = system.secondary,
                )
            }
        }

        RippleBadge(
            level = ripple?.rippleLevel ?: 0,
            activeToday = ripple?.activeToday ?: false,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .clickable(onClick = onProfile),
        )
    }
}
