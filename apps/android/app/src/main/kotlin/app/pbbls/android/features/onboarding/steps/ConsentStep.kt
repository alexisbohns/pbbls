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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.onboarding.OnboardingConsentState

/** The switch of [kind]'s row, for the tests. */
fun onboardingConsentTag(kind: ConsentKind): String = "onboarding_consent_${kind.wire}"

/**
 * Step 2 (#1510 D3): four switches, one per act, health first; an act already on record is on and
 * locked ("Already on record"), the rest are off until the user flips them, and Continue waits for
 * all four. Each flip records through `record_consent` before the switch settles.
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
        Text(
            stringResource(R.string.onboarding_consent_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_consent_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ConsentSwitchRow(
                ConsentKind.HEALTH_DATA,
                R.string.onboarding_consent_health,
                R.string.onboarding_consent_health_hint,
                state,
                onToggle,
            )
            ConsentSwitchRow(ConsentKind.TERMS, R.string.onboarding_consent_terms, null, state, onToggle)
            ConsentSwitchRow(ConsentKind.PRIVACY, R.string.onboarding_consent_privacy, null, state, onToggle)
            ConsentSwitchRow(ConsentKind.AGE_ASSURANCE, R.string.onboarding_consent_age, null, state, onToggle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AssistChip(
                onClick = { openLegalDoc(context, LegalDoc.TERMS) },
                label = { Text(stringResource(R.string.onboarding_consent_read_terms)) },
            )
            AssistChip(
                onClick = { openLegalDoc(context, LegalDoc.PRIVACY) },
                label = { Text(stringResource(R.string.onboarding_consent_read_policy)) },
            )
        }
        state.errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = state.canContinue,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

@Composable
private fun ConsentSwitchRow(
    kind: ConsentKind,
    @StringRes labelRes: Int,
    @StringRes hintRes: Int?,
    state: OnboardingConsentState,
    onToggle: (ConsentKind, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val recorded = kind in state.recorded
    val checked = recorded || kind in state.on
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            val hint = if (recorded) stringResource(R.string.onboarding_consent_recorded) else hintRes?.let { stringResource(it) }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(kind, it) },
            enabled = !recorded && kind !in state.saving,
            modifier = Modifier.testTag(onboardingConsentTag(kind)),
        )
    }
}
