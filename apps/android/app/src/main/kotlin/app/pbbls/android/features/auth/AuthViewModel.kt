package app.pbbls.android.features.auth

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "auth"

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false,
    val isSubmitting: Boolean = false,
    /** A resource id, never a message: raw SDK text must not reach a user (D9, #850). */
    @StringRes val authErrorRes: Int? = null,
    /**
     * True while the raw input contained a `+` that was just stripped — drives
     * the inline explanation. Lowercasing is silent on purpose (no error shown).
     */
    val showPlusError: Boolean = false,
) {
    val canSubmit: Boolean
        get() =
            AuthLogic.canSubmit(
                mode = mode,
                email = email,
                password = password,
                termsAccepted = termsAccepted,
                privacyAccepted = privacyAccepted,
                isSubmitting = isSubmitting,
            )
}

/**
 * State holder for the sign-in / sign-up form (#849).
 *
 * **What rotation cost here was the submit guard.** The form fields were
 * `rememberSaveable` and survived, but `isSubmitting` and `authErrorRes` were
 * plain `remember`. Turning the phone mid-request therefore reset `isSubmitting`
 * to false, which re-enabled the button — so a second sign-up could be fired
 * while the first was still in flight, and any error already on screen vanished
 * with it. Both live here now, and the fields come with them so the whole form
 * is one value.
 *
 * **It calls supabase-kt itself** rather than taking `onSubmit`/`onGoogleSignIn`
 * as suspend lambdas from `RootScreen`. Those lambdas were how the funnel stayed
 * previewable before there was anywhere else to put the call; with a ViewModel
 * there is, and the screen keeps a stateless content layer for the screenshots.
 *
 * **No `NonCancellable`, deliberately.** Unlike every other write in this
 * migration, an interrupted sign-in leaves nothing half-applied on the client:
 * the session arrives through `SupabaseService`'s app-lifetime `sessionStatus`
 * collector, not through this call's return, so a cancelled request either
 * authenticated (and the gate flips regardless) or did not. There is no
 * bookkeeping to protect.
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val supabase: SupabaseServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        private var hasStarted = false

        /**
         * Seed from the route's mode, once.
         *
         * Guarded so a rotation cannot put the user back on Login after they
         * switched to Sign up — the mode is a choice, not a route parameter, once
         * the screen is open.
         */
        fun start(initialMode: AuthMode) {
            if (hasStarted) return
            hasStarted = true
            _uiState.update {
                it.copy(
                    mode = savedState.get<String>(KEY_MODE)?.let { AuthMode.valueOf(it) } ?: initialMode,
                    email = savedState[KEY_EMAIL] ?: "",
                    termsAccepted = savedState[KEY_TERMS] ?: false,
                    privacyAccepted = savedState[KEY_PRIVACY] ?: false,
                )
            }
        }

        // MARK: - Form

        /**
         * `+` is stripped and explained; the lowercasing is silent (product
         * policy — the alias ban is the thing worth a message).
         */
        fun onEmailChange(raw: String) {
            val normalized = AuthLogic.normalizeEmailInput(raw)
            savedState[KEY_EMAIL] = normalized
            _uiState.update {
                it.copy(
                    email = normalized,
                    showPlusError = raw.contains("+"),
                    authErrorRes = null,
                )
            }
        }

        /** Never persisted — a password in the saved-state `Bundle` outlives the screen on disk. */
        fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

        fun onTermsChange(value: Boolean) {
            savedState[KEY_TERMS] = value
            _uiState.update { it.copy(termsAccepted = value) }
        }

        fun onPrivacyChange(value: Boolean) {
            savedState[KEY_PRIVACY] = value
            _uiState.update { it.copy(privacyAccepted = value) }
        }

        /** Switching to Login drops the consents: they belong to a sign-up. */
        fun onModeChange(newMode: AuthMode) {
            savedState[KEY_MODE] = newMode.name
            val clearsConsents = newMode == AuthMode.LOGIN
            if (clearsConsents) {
                savedState[KEY_TERMS] = false
                savedState[KEY_PRIVACY] = false
            }
            _uiState.update {
                it.copy(
                    mode = newMode,
                    authErrorRes = null,
                    termsAccepted = if (clearsConsents) false else it.termsAccepted,
                    privacyAccepted = if (clearsConsents) false else it.privacyAccepted,
                )
            }
        }

        fun dismissError() = _uiState.update { it.copy(authErrorRes = null) }

        // MARK: - Submit

        fun submit() {
            val state = _uiState.value
            if (!state.canSubmit) return
            _uiState.update { it.copy(isSubmitting = true, authErrorRes = null) }

            viewModelScope.launch {
                attempt("sign-in/up") {
                    when (state.mode) {
                        AuthMode.LOGIN -> supabase.signIn(state.email.trim(), state.password)
                        AuthMode.SIGNUP -> supabase.signUp(state.email.trim(), state.password)
                    }
                }
            }
        }

        fun signInWithGoogle() {
            if (_uiState.value.isSubmitting) return
            _uiState.update { it.copy(isSubmitting = true, authErrorRes = null) }
            viewModelScope.launch { attempt("google sign-in") { supabase.signInWithGoogle() } }
        }

        private suspend fun attempt(
            label: String,
            block: suspend () -> Unit,
        ) {
            runCatchingCancellable { block() }
                .onFailure {
                    Log.e(TAG, "$label failed", it)
                    _uiState.update { state -> state.copy(authErrorRes = authErrorMessage(it)) }
                }
            // On success the session lands through the auth-status collector and
            // the gate unmounts this screen, so this write is only ever seen on
            // the failure path — but it is unconditional so a success that does
            // NOT flip the gate (an unconfirmed sign-up) still frees the button.
            _uiState.update { it.copy(isSubmitting = false) }
        }

        private companion object {
            const val KEY_MODE = "auth-mode"
            const val KEY_EMAIL = "auth-email"
            const val KEY_TERMS = "auth-terms"
            const val KEY_PRIVACY = "auth-privacy"
        }
    }
