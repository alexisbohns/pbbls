package app.pbbls.android.features.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A single [WelcomeStep] as a centered title + description — the
 * `WelcomeSlideView` analog. Title is `titleLarge` (Ysabeau) and the
 * description `bodyLarge`, both in `onSurfaceVariant`.
 * No per-slide illustration — the logo in `WelcomeScreen`'s header fills
 * that role.
 */
@Composable
fun WelcomeSlideView(
    step: WelcomeStep,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
            style = MaterialTheme.typography.titleLarge,
            color = muted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(step.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}
