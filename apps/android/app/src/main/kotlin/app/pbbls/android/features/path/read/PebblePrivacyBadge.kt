package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.theme.PebblesTheme

/**
 * Privacy chip — ports the `.chip` style of iOS `PebblePrivacyBadge.swift` (the
 * nav-bar treatment): a 16dp lock centered in a 36dp box, `system.secondary`
 * tint. [visibility] is unused visually in v1 (every pebble is private) but kept
 * for parity and the future public badge (D17 renders public read-only).
 */
@Composable
fun PebblePrivacyBadge(
    visibility: Visibility,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = stringResource(R.string.pebble_detail_privacy_a11y),
            tint = system.secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
