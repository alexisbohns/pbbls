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
 * The Pebbles brand mark, inked in `primary` — the colour iOS
 * `HandcraftedLogoView` draws it in (`accent.primary`).
 *
 * `R.drawable.pbbls_logo` is generated from the same iOS SVG as the launcher
 * icon (`scripts/logo-svg-to-launcher-icon.mjs`), so the two surfaces share one
 * source. It replaced the Rive logo (#856): Rive's native library was loaded on
 * every cold start to play one animation on a screen signed-in users never see.
 * The iOS draw-on reveal is not ported; this is the settled, static frame.
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
