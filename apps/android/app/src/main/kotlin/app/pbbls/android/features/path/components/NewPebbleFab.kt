package app.pbbls.android.features.path.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage

/**
 * The Path tab's create affordance, now a Path-only FAB (#852, D3) — the bar
 * now occupies the space the pinned "New pebble" pill
 * used to sit in, so the affordance moved up and became
 * circular. A tap opens Capture (#1048); the long press to the all-at-once
 * composer is gone with it (A-12). The `primary` fill carries its
 * `onPrimary` icon.
 */
@Composable
fun NewPebbleFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.create_new_pebble)
    Box(
        modifier =
            modifier
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The FAB's 24dp icon slot: the generated default (spec D6).
        PbblsIconImage(
            icon = PbblsIcon.PLUS_CIRCLE,
            contentDescription = label,
            tint = colors.onPrimary,
        )
    }
}
