package app.pbbls.android.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.rive.RiveLogo
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Authenticated landing surface — a placeholder for sub-project C. The real
 * read-only Path timeline (`path_pebbles()` → week-grouped list) lands in
 * sub-project D; this proves the session gate and provides a temporary sign-out
 * so the funnel can be re-tested on device until Profile exists.
 */
@Composable
fun PathScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RiveLogo(modifier = Modifier.size(120.dp))
        Text(
            text = stringResource(R.string.path_placeholder_title),
            style = PebblesTypography.title,
            color = system.foreground,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.path_placeholder_subtitle),
            style = PebblesTypography.body,
            color = system.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = stringResource(R.string.path_sign_out),
                style = PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}
