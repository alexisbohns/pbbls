package app.pbbls.android.features.path.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesTheme

/**
 * The Path tab's create affordance, now a Path-only FAB (#852, D3) — the bar
 * now occupies the space the pinned "New pebble" pill
 * ([NewPebbleButton]) used to sit in, so the affordance moved up and became
 * circular. The tap/long-press pair is preserved verbatim (M58 D1): a tap
 * opens the step-by-step record flow, a long press opens the all-at-once
 * composer instead. `accent.primary` / `accent.light` are the same fill/label
 * pair [NewPebbleButton]'s pill used, carried over rather than invented.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewPebbleFab(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val label = stringResource(R.string.create_new_pebble)
    Box(
        modifier =
            modifier
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(accent.primary)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = label,
            tint = accent.light,
            modifier = Modifier.size(24.dp),
        )
    }
}
