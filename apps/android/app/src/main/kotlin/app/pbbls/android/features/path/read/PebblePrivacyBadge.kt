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
import app.pbbls.android.features.path.models.iconRes
import app.pbbls.android.features.path.models.labelRes
import app.pbbls.android.theme.PebblesTheme

/**
 * Privacy chip — ports the `.chip` style of iOS `PebblePrivacyBadge.swift` (the
 * nav-bar treatment): a 16dp per-grade icon ([Visibility.iconRes]) centered in
 * a 36dp box, `system.secondary` tint, labeled with the per-grade a11y string
 * (M51).
 */
@Composable
fun PebblePrivacyBadge(
    visibility: Visibility,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(visibility.iconRes),
            contentDescription = stringResource(R.string.visibility_badge_a11y, stringResource(visibility.labelRes)),
            tint = system.secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
