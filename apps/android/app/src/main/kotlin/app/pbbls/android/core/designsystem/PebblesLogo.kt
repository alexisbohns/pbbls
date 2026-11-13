package app.pbbls.android.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/**
 * The Pebbles brand mark (#1644): a pebble outline holding a spiral, with
 * a P at its centre, inked in `primary`.
 *
 * `R.drawable.pbbls_logo` is generated from the iOS master mark
 * (`apps/ios/Pebbles/Resources/pbbls-mark.svg`, one filled even-odd path) by
 * `scripts/logo-svg-to-launcher-icon.mjs`, the same run that produces the
 * launcher icon, so the surfaces share one source. It replaced the Rive logo
 * (#856): Rive's native library was loaded on every cold start to play one
 * animation on a screen signed-in users never see.
 * The splash animation is not ported here; this is the settled, static mark.
 */
@Composable
fun PebblesLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.pbbls_logo),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
    )
}
