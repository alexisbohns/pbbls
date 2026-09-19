package app.pbbls.android.features.auth

import android.util.Log
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.components.GoogleSignInButton
import app.pbbls.android.components.LegalDisclaimer
import app.pbbls.android.components.LegalDoc
import app.pbbls.android.components.PebblesAuthSwitcher
import app.pbbls.android.components.PebblesCheckbox
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.components.PebblesTextInput
import app.pbbls.android.components.openLegalDoc
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "auth"

private val ErrorRed = Color(0xFFDC2626)

/**
 * Email + password auth screen — the `AuthView` analog. The switcher toggles
 * Login/Sign-up; sign-up adds two consent checkboxes. Email is live-normalized
 * (lowercase, strip `+`), and typing a `+` surfaces an inline explanation. All
 * state is view-local; the actual auth calls are supplied as suspend lambdas
 * ([onSubmit]/[onGoogleSignIn]) so the screen stays previewable and business
 * logic lives at the NavHost binding layer.
 */
@Composable
fun AuthScreen(
    initialMode: AuthMode,
    onSubmit: suspend (AuthMode, String, String) -> Unit,
    onGoogleSignIn: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    // A resource id, never a message: raw SDK text must not reach a user (D9, #850).
    var authErrorRes by remember { mutableStateOf<Int?>(null) }
    // True while the raw input contained a '+' that was just stripped — drives the
    // inline explanation. Lowercasing is silent on purpose (no error shown).
    var showPlusError by remember { mutableStateOf(false) }

    val canSubmit =
        AuthLogic.canSubmit(
            mode = mode,
            email = email,
            password = password,
            termsAccepted = termsAccepted,
            privacyAccepted = privacyAccepted,
            isSubmitting = isSubmitting,
        )

    fun onEmailChange(raw: String) {
        if (authErrorRes != null) authErrorRes = null
        showPlusError = raw.contains("+")
        email = AuthLogic.normalizeEmailInput(raw)
    }

    fun onModeChange(newMode: AuthMode) {
        mode = newMode
        authErrorRes = null
        if (newMode == AuthMode.LOGIN) {
            termsAccepted = false
            privacyAccepted = false
        }
    }

    fun submit() {
        scope.launch {
            isSubmitting = true
            authErrorRes = null
            try {
                onSubmit(mode, email.trim(), password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sign-in/up failed", e)
                authErrorRes = authErrorMessage(e)
            }
            isSubmitting = false
        }
    }

    fun runGoogle() {
        if (isSubmitting) return
        scope.launch {
            isSubmitting = true
            authErrorRes = null
            try {
                onGoogleSignIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "google sign-in failed", e)
                authErrorRes = authErrorMessage(e)
            }
            isSubmitting = false
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.padding(top = 8.dp))

        PebblesAuthSwitcher(mode = mode, onModeChange = ::onModeChange)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PebblesTextInput(
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    value = email,
                    onValueChange = ::onEmailChange,
                    contentType = ContentType.EmailAddress,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                        ),
                )
                if (showPlusError) {
                    Text(
                        text = stringResource(R.string.auth_email_plus_error),
                        style = PebblesTypography.subhead.copy(fontSize = 12.sp),
                        color = ErrorRed,
                    )
                }
            }

            PebblesTextInput(
                placeholder = stringResource(R.string.auth_password_placeholder),
                value = password,
                onValueChange = {
                    if (authErrorRes != null) authErrorRes = null
                    password = it
                },
                isSecure = true,
                contentType = if (mode == AuthMode.LOGIN) ContentType.Password else ContentType.NewPassword,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
            )
        }

        if (mode == AuthMode.SIGNUP) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PebblesCheckbox(
                    isChecked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    prefix = stringResource(R.string.auth_consent_prefix),
                    linkText = stringResource(R.string.auth_consent_terms_link),
                    onLinkTap = { openLegalDoc(context, LegalDoc.TERMS) },
                )
                PebblesCheckbox(
                    isChecked = privacyAccepted,
                    onCheckedChange = { privacyAccepted = it },
                    prefix = stringResource(R.string.auth_consent_prefix),
                    linkText = stringResource(R.string.auth_consent_privacy_link),
                    onLinkTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
                )
            }
        }

        authErrorRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = PebblesTypography.subhead.copy(fontSize = 12.sp),
                color = ErrorRed,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PebblesPrimaryButton(
            text =
                stringResource(
                    if (mode == AuthMode.LOGIN) R.string.auth_submit_login else R.string.auth_submit_signup,
                ),
            onClick = ::submit,
            enabled = canSubmit,
            isLoading = isSubmitting,
        )

        // The screen scrolls (keyboard-safe), so OAuth follows the primary action
        // rather than being pinned to the bottom as on iOS — same content, order
        // preserved.
        GoogleSignInButton(onClick = ::runGoogle, enabled = !isSubmitting)

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
