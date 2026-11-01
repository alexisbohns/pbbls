package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.core.ui.RippleBadge

/** The badge on the line: below 32 dp it cannot hold its digit (spec D15). */
private val BADGE = 24.dp

/** Between the badge and the label (the mock's 7 px). */
private val GAP = 7.dp

/**
 * "Ripples Level 3" beside a small Ripples mark (#1383, spec D14.3 and D15). The badge drops its
 * digit, since the label says the level, and the line is one TalkBack node reading the badge's
 * own label ("Ripple level 3, active today"), the text node cleared.
 */
@Composable
internal fun RipplesLine(
    ripple: RippleSummary,
    modifier: Modifier = Modifier,
) {
    val level = ripple.rippleLevel.coerceIn(0, 6)
    val spoken =
        if (ripple.activeToday) {
            stringResource(R.string.ripple_badge_active, level)
        } else {
            stringResource(R.string.ripple_badge_inactive, level)
        }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RippleBadge(level = level, activeToday = ripple.activeToday, size = BADGE, showsLevel = false)
        Text(
            text = stringResource(R.string.profile_ripples_level, level),
            style = PebblesTheme.page.rippleLabel,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
