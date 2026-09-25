package app.pbbls.android.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/** The capsule is a pinned light surface: the multi-colour G mark requires one. */
internal val GoogleButtonSurface = Color.White

/**
 * "Continue with Google": a stock M3 `OutlinedButton` at the funnel's action
 * height, pinned to a white container because the multi-colour G mark requires
 * one (Google's branding rules), with a 1 dp `outlineVariant` border so it reads
 * against the page. Kept as a component because the logo + label layout
 * repeats on Welcome and Auth. (No Apple sign-in on Android — settled non-goal.)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(PebblesActionHeight),
        modifier = modifier.fillMaxWidth().heightIn(min = PebblesActionHeight),
        enabled = enabled,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = GoogleButtonSurface,
                contentColor = GoogleCapsuleInk,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = ButtonDefaults.contentPaddingFor(PebblesActionHeight),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.welcome_continue_google),
            style = ButtonDefaults.textStyleFor(PebblesActionHeight),
        )
    }
}
