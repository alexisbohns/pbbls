package app.pbbls.android.features.path.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Full-width "New pebble" entry pill pinned at the bottom of the Path timeline
 * and repeated in the empty-week affordance — the `NewPebbleButton.swift`
 * analog. `system.muted` fill, `accent.primary` label. Pattern:
 * [PebblesPrimaryButton] (fill + clip + clickable Box).
 *
 * A tap opens the step-by-step record flow; [onLongPress] opens the all-at-once
 * composer instead (M58 D1). Two composers is a cost accepted deliberately and
 * temporarily: the flow is an experiment in interaction model, and the honest
 * way to evaluate it is to be able to fall back on device without a rebuild.
 * Long-press was chosen over a Settings toggle because it adds no chrome, no
 * persisted state and no localized string — it deletes in one line when the
 * experiment resolves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewPebbleButton(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(system.muted)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        PebblesText(
            text = stringResource(R.string.create_new_pebble),
            style = PebblesTypography.buttonLabel.copy(fontSize = 20.sp),
            color = accent.primary,
        )
    }
}
