package app.pbbls.android.features.path.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 */
@Composable
fun NewPebbleButton(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(system.muted)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        PebblesText(
            text = stringResource(R.string.create_new_pebble),
            style = PebblesTypography.buttonLabel.copy(fontSize = 20.sp),
            color = accent.primary,
        )
    }
}
