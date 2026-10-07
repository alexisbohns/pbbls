package app.pbbls.android.features.consent

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ConsentPreferences
import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.ConsentKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "consent"

sealed interface ConsentGateUiState {
    /** No signed-in user: nothing to gate. */
    data object Idle : ConsentGateUiState

    data object Checking : ConsentGateUiState

    /** [userId] passed. Carried so a stale pass can never let a different user through. */
    data class Satisfied(
        val userId: String,
    ) : ConsentGateUiState

    data class Required(
        val missing: List<ConsentKind>,
        val ticked: Set<ConsentKind> = emptySet(),
        val isSubmitting: Boolean = false,
        @StringRes val errorRes: Int? = null,
    ) : ConsentGateUiState {
        val canContinue: Boolean by lazy { !isSubmitting && ticked.containsAll(missing) }
    }

    /** The ledger could not be read. Fails closed (design D4). */
    data class Failed(
        @StringRes val messageRes: Int,
    ) : ConsentGateUiState
}

/**
 * Holds a signed-in user behind the consent gate until the ledger carries every
 * act `ConsentGateLogic.REQUIRED` names at a current version (design §5.3).
 *
 * Owned by `RootScreen`, not by a back-stack entry: the gate is an overlay
 * above `NavDisplay` (design D9), so nothing that navigates can route around
 * it. `RootScreen` calls [start] whenever the session's user id changes.
 */
@HiltViewModel
class ConsentGateViewModel
    @Inject
    constructor(
        private val consents: ConsentServicing,
        private val cache: ConsentPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ConsentGateUiState>(ConsentGateUiState.Idle)
        val uiState: StateFlow<ConsentGateUiState> = _uiState.asStateFlow()

        private var userId: String? = null

        /**
         * Idempotent for the same user: `RootScreen` re-runs it on every
         * activity recreation, and a check already in flight (or a verdict
         * already reached) must not restart.
         */
        fun start(userId: String?) {
            if (userId == this.userId && _uiState.value != ConsentGateUiState.Idle) return
            this.userId = userId
            when {
                userId == null -> _uiState.value = ConsentGateUiState.Idle
                cache.isSatisfied(userId, ConsentGateLogic.fingerprint()) ->
                    _uiState.value = ConsentGateUiState.Satisfied(userId)
                else -> check(userId)
            }
        }

        fun retry() {
            userId?.let { check(it) }
        }

        fun onToggle(
            kind: ConsentKind,
            checked: Boolean,
        ) {
            _uiState.update { state ->
                if (state !is ConsentGateUiState.Required || state.isSubmitting) return@update state
                state.copy(ticked = if (checked) state.ticked + kind else state.ticked - kind, errorRes = null)
            }
        }

        fun onContinue() {
            val state = _uiState.value as? ConsentGateUiState.Required ?: return
            val uid = userId ?: return
            if (!state.canContinue) return
            _uiState.value = state.copy(isSubmitting = true, errorRes = null)

            viewModelScope.launch {
                // One idempotent record_consent per act, as web's OAuth callback
                // does. The acts are independent, so there is no cross-row
                // invariant for a batch RPC to protect; if one fails, the
                // re-read below shows only what is still missing.
                runCatchingCancellable {
                    state.missing.forEach { consents.record(it, ConsentGateLogic.versionFor(it), ConsentGateLogic.SOURCE) }
                    consents.active()
                }.onSuccess { active ->
                    if (uid != userId) return@onSuccess
                    resolve(uid, ConsentGateLogic.missing(active))
                }.onFailure { error ->
                    Log.e(TAG, "record consent failed", error)
                    if (uid != userId) return@onFailure
                    _uiState.update {
                        (it as? ConsentGateUiState.Required)
                            ?.copy(isSubmitting = false, errorRes = consentErrorMessage(error.toDataError()))
                            ?: it
                    }
                }
            }
        }

        private fun check(uid: String) {
            _uiState.value = ConsentGateUiState.Checking
            viewModelScope.launch {
                runCatchingCancellable { consents.active() }
                    .onSuccess { active ->
                        if (uid != userId) return@onSuccess
                        resolve(uid, ConsentGateLogic.missing(active))
                    }.onFailure { error ->
                        Log.e(TAG, "consent check failed", error)
                        if (uid != userId) return@onFailure
                        _uiState.value = ConsentGateUiState.Failed(consentErrorMessage(error.toDataError()))
                    }
            }
        }

        private fun resolve(
            uid: String,
            missing: List<ConsentKind>,
        ) {
            if (missing.isEmpty()) {
                cache.markSatisfied(uid, ConsentGateLogic.fingerprint())
                _uiState.value = ConsentGateUiState.Satisfied(uid)
            } else {
                _uiState.value = ConsentGateUiState.Required(missing)
            }
        }
    }

@StringRes
internal fun consentErrorMessage(error: DataError): Int =
    when (error) {
        DataError.Network -> R.string.error_offline
        DataError.Unauthorized,
        DataError.NotFound,
        DataError.Quota,
        is DataError.Conflict,
        is DataError.Unknown,
        -> R.string.consent_gate_error
    }
