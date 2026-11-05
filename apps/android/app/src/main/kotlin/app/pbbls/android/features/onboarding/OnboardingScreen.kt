package app.pbbls.android.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.readableWidth

/**
 * The onboarding entry (#1510). A temporary shell until the seven-step flow lands: the promise
 * step's title and one button that finishes, so the gate, the hand-off and the consent gate's
 * deferral can be built and tested against the real entry. [steps] is the config the flow will
 * walk; [onFinish] is the call site's (`RootScreen` marks the device, the profile and restarts the
 * consent gate).
 *
 * Pushed as an ordinary [app.pbbls.android.navigation.PebblesKey.Onboarding] entry (#852). System
 * back is consumed entirely rather than popping the entry — the flow is left only by finishing it
 * (D10) — via [NavigationBackHandler], not the legacy `BackHandler`, so a completed gesture never
 * reaches `NavDisplay`'s own predictive-pop transition.
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    // Skipped under @Preview/screenshot rendering, where no NavigationEventDispatcherOwner is provided.
    if (!LocalInspectionMode.current) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { /* consume — no-op */ }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .readableWidth()
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.onboarding_promise_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.onboarding_progress, 1, steps.count { it.id != "handoff" }),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            PebblesPrimaryButton(
                text = stringResource(R.string.onboarding_promise_cta),
                onClick = onFinish,
                // The baseline-profile journey taps this tag to reach Home past a first run.
                modifier = Modifier.fillMaxWidth().testTag(JourneyTags.ONBOARDING_SKIP),
            )
        }
    }
}
