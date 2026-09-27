package app.pbbls.android.features.consent

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesCheckbox
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.ConsentKind

/**
 * The consent gate (design §5.3), drawn by `RootScreen` above everything else.
 *
 * A `Surface`, so it swallows every touch aimed at the app beneath it. Back
 * sends the app to the background rather than doing nothing: a screen that
 * traps Back is worse than one that leaves, and leaving grants nothing. The
 * handler outranks `NavDisplay`'s because it registers later, the same
 * arrangement `AchievementMomentOverlay` relies on.
 */
@Composable
fun ConsentGateScreen(
    uiState: ConsentGateUiState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = true) {
        activity?.moveTaskToBack(true)
    }
    ConsentGateContent(
        uiState = uiState,
        onToggle = onToggle,
        onContinue = onContinue,
        onRetry = onRetry,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

/** Stateless content layer, what the screenshots render. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConsentGateContent(
    uiState: ConsentGateUiState,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (uiState) {
            // Idle and Satisfied are never shown by RootScreen; a blank surface is
            // the safe render if they ever are.
            ConsentGateUiState.Idle,
            is ConsentGateUiState.Satisfied,
            -> Unit

            ConsentGateUiState.Checking ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { LoadingIndicator() }

            is ConsentGateUiState.Failed ->
                GateColumn {
                    Text(stringResource(uiState.messageRes), style = MaterialTheme.typography.bodyLarge)
                    PebblesPrimaryButton(text = stringResource(R.string.consent_gate_retry), onClick = onRetry)
                    SignOutButton(onSignOut)
                }

            is ConsentGateUiState.Required ->
                GateColumn {
                    Text(
                        stringResource(R.string.consent_gate_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.consent_gate_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        uiState.missing.forEach { kind ->
                            ConsentRow(kind, kind in uiState.ticked) { onToggle(kind, it) }
                        }
                    }
                    uiState.errorRes?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    PebblesPrimaryButton(
                        text = stringResource(R.string.consent_gate_continue),
                        onClick = onContinue,
                        enabled = uiState.canContinue,
                        isLoading = uiState.isSubmitting,
                        modifier = Modifier.testTag(CONSENT_GATE_CONTINUE),
                    )
                    SignOutButton(onSignOut)
                }
        }
    }
}

@Composable
private fun GateColumn(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) { content() }
}

@Composable
private fun ConsentRow(
    kind: ConsentKind,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val tag = Modifier.testTag(consentRowTag(kind))
    when (kind) {
        ConsentKind.TERMS ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                prefix = stringResource(R.string.auth_consent_prefix),
                linkText = stringResource(R.string.auth_consent_terms_link),
                onLinkTap = { openLegalDoc(context, LegalDoc.TERMS) },
                modifier = tag,
            )
        ConsentKind.PRIVACY ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                prefix = stringResource(R.string.auth_consent_prefix),
                linkText = stringResource(R.string.auth_consent_privacy_link),
                onLinkTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
                modifier = tag,
            )
        ConsentKind.HEALTH_DATA ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                label = stringResource(R.string.auth_consent_health),
                modifier = tag,
            )
        ConsentKind.AGE_ASSURANCE ->
            PebblesCheckbox(
                isChecked = checked,
                onCheckedChange = onChange,
                label = stringResource(R.string.auth_consent_age),
                modifier = tag,
            )
    }
}

@Composable
private fun SignOutButton(onSignOut: () -> Unit) {
    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.consent_gate_sign_out))
    }
}

internal const val CONSENT_GATE_CONTINUE = "consent_gate_continue"

internal fun consentRowTag(kind: ConsentKind) = "consent_gate_${kind.wire}"
