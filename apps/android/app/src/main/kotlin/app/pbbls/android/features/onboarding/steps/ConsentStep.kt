package app.pbbls.android.features.onboarding.steps

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.ListGroup
import app.pbbls.android.core.designsystem.ListGroupItemScope
import app.pbbls.android.core.designsystem.ListGroupSwitchItem
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.onboarding.OnboardingConsentState

/** The switch of [kind]'s row, for the tests. */
fun onboardingConsentTag(kind: ConsentKind): String = "onboarding_consent_${kind.wire}"

/**
 * Step 2 (#1510 D3): one switch per act, on the app's own settings rows. A switch turns on and off
 * freely; Continue waits for all four and records them, so nothing has to be taken back. An act
 * already on record stays on: withdrawing one is a Settings action, not a side effect of this step.
 */
@Composable
fun ConsentStep(
    state: OnboardingConsentState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        StepTitle(stringResource(R.string.onboarding_consent_title))
        Text(stringResource(R.string.onboarding_consent_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        ListGroup(
            items =
                listOf(
                    consentRow(
                        ConsentKind.HEALTH_DATA,
                        R.string.onboarding_consent_health,
                        R.string.onboarding_consent_health_detail,
                        state,
                        onToggle,
                    ),
                    consentRow(
                        ConsentKind.TERMS,
                        R.string.onboarding_consent_terms,
                        R.string.onboarding_consent_terms_detail,
                        state,
                        onToggle,
                    ),
                    consentRow(
                        ConsentKind.PRIVACY,
                        R.string.onboarding_consent_privacy,
                        R.string.onboarding_consent_privacy_detail,
                        state,
                        onToggle,
                    ),
                    consentRow(
                        ConsentKind.AGE_ASSURANCE,
                        R.string.onboarding_consent_age,
                        R.string.onboarding_consent_age_detail,
                        state,
                        onToggle,
                    ),
                ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { openLegalDoc(context, LegalDoc.TERMS) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_consent_read_terms))
            }
            OutlinedButton(onClick = { openLegalDoc(context, LegalDoc.PRIVACY) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_consent_read_policy))
            }
        }
        state.errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!state.canContinue && !state.isSaving) {
                Text(
                    stringResource(R.string.onboarding_consent_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            }
            PebblesPrimaryButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                enabled = state.canContinue,
                isLoading = state.isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(JourneyTags.ONBOARDING_NEXT),
            )
        }
    }
}

private fun consentRow(
    kind: ConsentKind,
    @StringRes labelRes: Int,
    @StringRes detailRes: Int,
    state: OnboardingConsentState,
    onToggle: (ConsentKind, Boolean) -> Unit,
): @Composable ListGroupItemScope.() -> Unit =
    {
        val recorded = kind in state.recorded
        ListGroupSwitchItem(
            headline = stringResource(labelRes),
            checked = recorded || kind in state.on,
            onCheckedChange = { onToggle(kind, it) },
            supporting = stringResource(if (recorded) R.string.onboarding_consent_recorded else detailRes),
            modifier = Modifier.testTag(onboardingConsentTag(kind)),
        )
    }
