package app.pbbls.android.features.path.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * A tap opens Capture (#1048). The long press to the all-at-once composer
 * (M58 D1) was retired with it (A-12).
 */
@Composable
fun NewPebbleButton(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.create_new_pebble),
            style = MaterialTheme.typography.titleLarge,
            color = colors.primary,
        )
    }
}
