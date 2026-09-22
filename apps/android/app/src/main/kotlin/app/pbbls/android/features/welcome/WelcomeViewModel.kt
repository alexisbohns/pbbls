package app.pbbls.android.features.welcome

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.ui.authErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "welcome"

/**
 * Welcome's only state: whether a Google sign-in is in flight, and why the last
 * one failed.
 *
 * The reveal animation deliberately stays in the composable. It is presentation
 * driven by a `LaunchedEffect` over `contentRevealed` and the reduce-motion
 * setting, with nothing to survive a rotation — putting it here would make the
 * hero animation replay-or-not depend on a ViewModel, which is not a decision
 * state should be making.
 */
data class WelcomeUiState(
    val isSubmitting: Boolean = false,
    /** A resource id, never a message: raw SDK text must not reach a user (D9, #850). */
    @StringRes val authErrorRes: Int? = null,
)

/**
 * State holder for the Welcome hero (#849).
 *
 * The same rotation gap as [app.pbbls.android.features.auth.AuthViewModel], one
 * button wide: `isSubmitting` was a plain `remember`, so turning the phone while
 * the Google flow was open re-enabled the button and dropped any error already
 * shown.
 *
 * It calls supabase-kt itself rather than taking `onGoogleSignIn` as a suspend
 * lambda from `RootScreen`; the stateless `WelcomeContent` is what the
 * screenshots drive, so the funnel stays previewable without the hoist.
 */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val supabase: SupabaseServicing,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WelcomeUiState())
        val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

        fun dismissError() = _uiState.update { it.copy(authErrorRes = null) }

        fun signInWithGoogle() {
            if (_uiState.value.isSubmitting) return
            _uiState.update { it.copy(isSubmitting = true, authErrorRes = null) }

            viewModelScope.launch {
                runCatchingCancellable { supabase.signInWithGoogle() }
                    .onFailure {
                        Log.e(TAG, "google sign-in failed", it)
                        _uiState.update { state -> state.copy(authErrorRes = authErrorMessage(it)) }
                    }
                // A success unmounts this screen through the auth gate, so this
                // is normally only seen on failure — but it is unconditional so
                // a cancelled OAuth flow still frees the button.
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }
