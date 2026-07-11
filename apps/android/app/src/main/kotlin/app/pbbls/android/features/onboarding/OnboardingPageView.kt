package app.pbbls.android.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Renders a single [OnboardingStep] — the `OnboardingPageView` analog: a large
 * illustration card, title, and body. The illustration switches on the image
 * type; [OnboardingImage.Placeholder] shows the accent surface until real
 * artwork is exported (risk 6).
 */
@Composable
fun OnboardingPageView(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(accent.surface),
        ) {
            when (val image = step.image) {
                is OnboardingImage.Asset -> {
                    Image(
                        painter = painterResource(image.resId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                OnboardingImage.Placeholder -> {
                    Unit
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(step.titleRes),
                style = PebblesTypography.title.copy(fontSize = 24.sp),
                color = system.foreground,
            )
            Text(
                text = stringResource(step.descriptionRes),
                style = PebblesTypography.body,
                color = system.secondary,
            )
        }
    }
}
