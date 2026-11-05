package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.OnboardingPace

/** The button of [pace], for the tests. */
fun onboardingPaceTag(pace: OnboardingPace): String = "onboarding_pace_${pace.slug}"

/**
 * Step 6 (#1510 D11, D12): the four paces as one vertical M3 connected button group (`ToggleButton`s
 * with the connected leading / middle / trailing shapes, the chosen one filled), "No streaks, no
 * guilt." under it. The last button is "Start": it sends everything (`complete_onboarding`), so it
 * loads while [isCompleting] and [errorRes] is the failed send's line.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaceStep(
    selected: OnboardingPace?,
    isCompleting: Boolean,
    errorRes: Int?,
    onSelect: (OnboardingPace) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.onboarding_pace_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_pace_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val paces = OnboardingPace.entries
            paces.forEachIndexed { index, pace ->
                ToggleButton(
                    checked = pace == selected,
                    onCheckedChange = { onSelect(pace) },
                    enabled = !isCompleting,
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            paces.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.RadioButton }
                            .testTag(onboardingPaceTag(pace)),
                ) { Text(stringResource(pace.labelRes)) }
            }
        }
        Text(stringResource(R.string.onboarding_pace_footer), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_finish),
            onClick = onFinish,
            enabled = !isCompleting,
            isLoading = isCompleting,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
