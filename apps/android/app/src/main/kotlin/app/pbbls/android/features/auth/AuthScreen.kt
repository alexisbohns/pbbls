package app.pbbls.android.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.GoogleSignInButton
import app.pbbls.android.core.designsystem.LegalDisclaimer
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesAuthSwitcher
import app.pbbls.android.core.designsystem.PebblesCheckbox
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTextInput
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.AuthMode

private const val TAG = "auth"

/**
 * Email + password auth screen — the `AuthView` analog. The switcher toggles
 * Login/Sign-up; sign-up adds two consent checkboxes. Email is live-normalized
 * (lowercase, strip `+`), and typing a `+` surfaces an inline explanation.
 *
 * [AuthViewModel] owns the form and the auth calls (#849); this function binds
 * them to [AuthContent], which is the stateless layer the screenshots drive.
 * The suspend lambdas the NavHost used to supply are gone with it.
 */
@Composable
fun AuthScreen(
    initialMode: AuthMode,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Guarded, so a rotation cannot put the user back on Login after they
    // switched to Sign up.
    LaunchedEffect(initialMode) { viewModel.start(initialMode) }

    AuthContent(
        uiState = uiState,
        onModeChange = viewModel::onModeChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTermsChange = viewModel::onTermsChange,
        onPrivacyChange = viewModel::onPrivacyChange,
        onDismissError = viewModel::dismissError,
        onSubmit = viewModel::submit,
        onGoogleSignIn = viewModel::signInWithGoogle,
        modifier = modifier,
    )
}

/**
 * Stateless auth form — what the screenshots drive. Takes its state and its
 * callbacks rather than reading a ViewModel, so it renders without Hilt.
 */
@Composable
fun AuthContent(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTermsChange: (Boolean) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.padding(top = 8.dp))

        PebblesAuthSwitcher(mode = uiState.mode, onModeChange = onModeChange)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PebblesTextInput(
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    contentType = ContentType.EmailAddress,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                )
                if (uiState.showPlusError) {
                    Text(
                        text = stringResource(R.string.auth_email_plus_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            PebblesTextInput(
                placeholder = stringResource(R.string.auth_password_placeholder),
                value = uiState.password,
                onValueChange = {
                    if (uiState.authErrorRes != null) onDismissError()
                    onPasswordChange(it)
                },
                isSecure = true,
                contentType = if (uiState.mode == AuthMode.LOGIN) ContentType.Password else ContentType.NewPassword,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
            )
        }

        if (uiState.mode == AuthMode.SIGNUP) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PebblesCheckbox(
                    isChecked = uiState.termsAccepted,
                    onCheckedChange = onTermsChange,
                    prefix = stringResource(R.string.auth_consent_prefix),
                    linkText = stringResource(R.string.auth_consent_terms_link),
                    onLinkTap = { openLegalDoc(context, LegalDoc.TERMS) },
                )
                PebblesCheckbox(
                    isChecked = uiState.privacyAccepted,
                    onCheckedChange = onPrivacyChange,
                    prefix = stringResource(R.string.auth_consent_prefix),
                    linkText = stringResource(R.string.auth_consent_privacy_link),
                    onLinkTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
                )
            }
        }

        uiState.authErrorRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PebblesPrimaryButton(
            text =
                stringResource(
                    if (uiState.mode == AuthMode.LOGIN) R.string.auth_submit_login else R.string.auth_submit_signup,
                ),
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            isLoading = uiState.isSubmitting,
        )

        // The screen scrolls (keyboard-safe), so OAuth follows the primary action
        // rather than being pinned to the bottom as on iOS — same content, order
        // preserved.
        GoogleSignInButton(onClick = onGoogleSignIn, enabled = !uiState.isSubmitting)

        LegalDisclaimer(
            onTermsTap = { openLegalDoc(context, LegalDoc.TERMS) },
            onPrivacyTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 32.dp),
        )
    }
}
