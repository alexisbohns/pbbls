package app.pbbls.android.features.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * A single [WelcomeStep] as a centered title + description — the
 * `WelcomeSlideView` analog. Title uses the Ysabeau display face (the `title`
 * token dialed to 22sp) in `system.secondary`; description keeps the body font.
 * No per-slide illustration — the Rive logo in `WelcomeScreen`'s header fills
 * that role.
 */
@Composable
fun WelcomeSlideView(
    step: WelcomeStep,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(step.titleRes),
            style = PebblesTypography.title.copy(fontSize = 22.sp),
            color = system.secondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(step.descriptionRes),
            style = PebblesTypography.body,
            color = system.secondary,
            textAlign = TextAlign.Center,
        )
    }
}
