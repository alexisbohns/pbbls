package app.pbbls.android.features.path.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * Full-width "New pebble" entry pill — the `NewPebbleButton.swift` analog.
 * `surfaceContainerHighest` fill, `primary` label. Pattern: [PebblesPrimaryButton]
 * (fill + clip + clickable Box).
 *
 * Formerly also pinned at the bottom of the Path timeline; #852 moved that
 * spot to [app.pbbls.android.features.path.components.NewPebbleFab] (the bar
 * now occupies the space it used), so this component is left only in the
 * empty-week affordance ([WeekPebbleList]'s `EmptyWeek`), which the FAB does
 * not cover since it is not scoped to a single page.
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
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHighest)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.create_new_pebble),
            style = MaterialTheme.typography.titleLarge,
            color = colors.primary,
        )
    }
}
