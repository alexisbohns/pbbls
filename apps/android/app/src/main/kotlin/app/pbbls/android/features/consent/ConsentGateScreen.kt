package app.pbbls.android.features.consent

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.data.AccountDeletionUi
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ReauthMethod
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesCheckbox
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.ReauthDialog
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
 *
 * "Delete my account" (#1030) opens the same confirm, recent sign-in and
 * failure dialogs as Settings, driven by [deletion]. On success the session
 * drops, `RootScreen` stops gating, and the user lands on Welcome.
 */
@Composable
fun ConsentGateScreen(
    uiState: ConsentGateUiState,
    deletion: AccountDeletionUi,
    onToggle: (ConsentKind, Boolean) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissDeleteError: () -> Unit,
    onReauthPasswordChange: (String) -> Unit,
    onSubmitReauth: () -> Unit,
    onCancelReauth: () -> Unit,
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
        onDeleteAccount = onDeleteAccount,
        isDeleting = deletion.deletion == DeletionState.DELETING,
        canDelete = deletion.deletion == DeletionState.IDLE,
        modifier = modifier,
    )

    if (deletion.deletion == DeletionState.CONFIRMING) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.consent_gate_delete_title),
            message = stringResource(R.string.consent_gate_delete_message),
            confirmText = stringResource(R.string.settings_delete_account_confirm),
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }
    if (deletion.deletion == DeletionState.FAILED) {
        DeleteErrorDialog(
            message = stringResource(R.string.consent_gate_delete_error),
            onDismiss = onDismissDeleteError,
        )
    }
    deletion.reauth?.let { reauth ->
        ReauthDialog(
            usesPassword = reauth.method == ReauthMethod.PASSWORD,
            password = reauth.password,
            onPasswordChange = onReauthPasswordChange,
            isWorking = reauth.isWorking,
            errorText = reauth.errorRes?.let { stringResource(it) },
            onConfirm = onSubmitReauth,
            onDismiss = onCancelReauth,
        )
    }
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
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
    canDelete: Boolean = true,
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
                    DeleteAccountButton(onDeleteAccount, isDeleting = isDeleting, enabled = canDelete)
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
                    DeleteAccountButton(onDeleteAccount, isDeleting = isDeleting, enabled = canDelete)
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

/**
 * Always offered, in error colour (orange in this theme): it is the one
 * irreversible action on the gate, and its dialog says so before anything runs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteAccountButton(
    onClick: () -> Unit,
    isDeleting: Boolean,
    enabled: Boolean,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(CONSENT_GATE_DELETE),
    ) {
        if (isDeleting) {
            LoadingIndicator(color = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        } else {
            Text(stringResource(R.string.consent_gate_delete_account), color = MaterialTheme.colorScheme.error)
        }
    }
}

internal const val CONSENT_GATE_DELETE = "consent_gate_delete"

internal const val CONSENT_GATE_CONTINUE = "consent_gate_continue"

internal fun consentRowTag(kind: ConsentKind) = "consent_gate_${kind.wire}"
