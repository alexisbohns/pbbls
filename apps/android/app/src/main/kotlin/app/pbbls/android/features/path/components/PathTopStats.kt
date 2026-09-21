package app.pbbls.android.features.path.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
 * Path's karma + Ripples status, above the week roll.
 *
 * Replaces `PathBottomBar` (#852). That bar sat where the four-tab
 * [app.pbbls.android.navigation.PebblesNavigationBar] now sits, and stacking the
 * two cost roughly 140dp of a phone's height — the same cost D3 rejected when it
 * moved "New pebble" to a FAB, so accepting it here would have been
 * inconsistent. Karma and Ripples are Path-specific *status* rather than
 * navigation, so they move rather than disappear.
 *
 * Its profile button did not move: it navigated to `You`, which is a tab now, so
 * it was pure duplication. That is also why the stats are no longer tap targets
 * — they only ever routed to Profile as a stand-in. A future Ripples explainer
 * sheet is still the natural owner of a tap here, and the two stay separate
 * elements so wiring one needs no restructuring.
 */
@Composable
fun PathTopStats(
    karma: Int?,
    ripple: RippleSummary?,
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
        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            // One combined target for TalkBack — the iOS accessibilityLabel analog.
            modifier = Modifier.clearAndSetSemantics { contentDescription = karmaLabel },
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
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
