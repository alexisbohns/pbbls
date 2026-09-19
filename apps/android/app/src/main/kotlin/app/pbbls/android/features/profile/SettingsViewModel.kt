package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.SupabaseServicing
import app.pbbls.android.services.toDataError
import app.pbbls.android.ui.UiEffects
import app.pbbls.android.ui.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "settings"

/** What the screen opened with — the server's truth, for the dirty comparison. */
data class SettingsInitial(
    val displayName: String = "",
    val glyphId: String? = null,
    val glyphStrokes: List<GlyphStroke>? = null,
    val handle: String? = null,
    val publicProfile: Boolean = false,
    val email: String? = null,
    val providers: List<String> = emptyList(),
)

/** What the user has typed. */
data class SettingsForm(
    val displayName: String = "",
    val handle: String = "",
    val isPublicProfile: Boolean = false,
    val newPassword: String = "",
    val pickedGlyph: Glyph? = null,
)

/**
 * The account-deletion flow as one value.
 *
 * Three independent booleans before (`showDeleteConfirm`, `isDeleting`,
 * `showDeleteError`) — eight combinations of which four are real, and
 * "confirming while deleting" was reachable.
 */
enum class DeletionState { IDLE, CONFIRMING, DELETING, FAILED }

data class SettingsUiState(
    val initial: SettingsInitial = SettingsInitial(),
    val form: SettingsForm = SettingsForm(),
    val isSaving: Boolean = false,
    @StringRes val handleErrorRes: Int? = null,
    val didSaveFail: Boolean = false,
    val isPresentingGlyphPicker: Boolean = false,
    val deletion: DeletionState = DeletionState.IDLE,
) {
    /** Handles are stored normalized (DB CHECK), so compare and write that form. */
    val normalizedHandle: String
        get() = form.handle.trim().lowercase()

    val isDirty: Boolean
        get() =
            settingsIsDirty(
                initialName = initial.displayName,
                name = form.displayName,
                initialGlyphId = initial.glyphId,
                pickedGlyphId = form.pickedGlyph?.id,
                newPassword = form.newPassword,
                initialHandle = initial.handle,
                handle = form.handle,
                initialPublicProfile = initial.publicProfile,
                isPublicProfile = form.isPublicProfile,
            )

    val currentStrokes: List<GlyphStroke>?
        get() = form.pickedGlyph?.strokes ?: initial.glyphStrokes

    /**
     * Reflects what is live on the server, not staged edits — a link to an
     * unsaved handle would 404.
     */
    val shareUrl: String?
        get() =
            initial.handle
                ?.takeIf { initial.publicProfile && it.isNotEmpty() }
                ?.let { "https://www.pbbls.app/u/$it" }
}

/** One-shot results the hosting screen acts on. */
sealed interface SettingsEffect {
    data class Saved(
        val displayName: String,
        val glyph: Glyph?,
        val handle: String?,
        val isPublic: Boolean,
    ) : SettingsEffect

    data object Dismiss : SettingsEffect
}

/**
 * State holder for Settings (#849).
 *
 * **The bug this exists for.** `save()` ran in `rememberCoroutineScope` and
 * makes up to three sequential server calls — `set_handle`, then
 * `update_profile` + the GoTrue password update, then `set_public_profile`.
 * Leaving the composition mid-sequence cancelled the coroutine between them,
 * and the order is load-bearing: the handle has to be stored before the
 * `public_profile` write passes its DB CHECK. So a cancel after the first call
 * left the handle **claimed** and the toggle **unwritten** — a half-applied
 * save the user cannot see and cannot repeat, because the handle they were
 * claiming is now taken by themselves.
 *
 * The writes run in [viewModelScope], and the whole sequence is wrapped in
 * `withContext(NonCancellable)`. Wrapping the *whole* sequence rather than its
 * tail is deliberate: there is no point at which stopping leaves the account
 * consistent, and the screen already blocks Cancel while saving, so nothing is
 * being taken away from the user.
 *
 * **[SavedStateHandle] deliberately excludes the password.** The handle is
 * written into the saved-instance-state `Bundle`, which Android persists to
 * disk; a new password sitting in it would outlive the screen on storage. The
 * three fields that do persist are the ones a user would be annoyed to retype,
 * and none of them is a secret. The picked glyph is not persisted either — it
 * is one tap to re-pick and would mean serializing its strokes.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val profileService: ProfileServicing,
        private val supabase: SupabaseServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<SettingsEffect>(viewModelScope)
        val effects: Flow<SettingsEffect> = effectsOut.flow

        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private var hasStarted = false

        /**
         * Seed from the profile the hosting screen already loaded, then overlay
         * whatever survived process death.
         *
         * Guarded so a recomposition or a rotation cannot re-seed over what the
         * user has typed since — unlike the record flow, whose guard has to live
         * in its draft coordinator because the seed itself arrives late.
         */
        fun start(initial: SettingsInitial) {
            if (hasStarted) return
            hasStarted = true
            _uiState.update {
                it.copy(
                    initial = initial,
                    form =
                        SettingsForm(
                            displayName = savedState[KEY_NAME] ?: initial.displayName,
                            handle = savedState[KEY_HANDLE] ?: initial.handle.orEmpty(),
                            isPublicProfile = savedState[KEY_PUBLIC] ?: initial.publicProfile,
                        ),
                )
            }
        }

        // MARK: - Form edits

        fun onDisplayNameChange(value: String) {
            savedState[KEY_NAME] = value
            _uiState.update { it.copy(form = it.form.copy(displayName = value)) }
        }

        fun onHandleChange(value: String) {
            savedState[KEY_HANDLE] = value
            // Emptying the field means "release my handle", which the server
            // pairs with dropping the public flag. Mirrored here so a save can
            // never carry "public, no handle" — the DB CHECK would reject it,
            // and the pairing is an invariant of the form, not of the layout it
            // used to live in.
            val releasing = value.trim().isEmpty()
            if (releasing) savedState[KEY_PUBLIC] = false
            _uiState.update {
                it.copy(
                    form =
                        it.form.copy(
                            handle = value,
                            isPublicProfile = if (releasing) false else it.form.isPublicProfile,
                        ),
                    // A new attempt clears the previous verdict, so the field
                    // never shows "taken" against a handle since edited.
                    handleErrorRes = null,
                )
            }
        }

        /** The toggle row, which is inert until a handle exists on the server. */
        fun togglePublicProfile() = onPublicProfileChange(!_uiState.value.form.isPublicProfile)

        fun onPublicProfileChange(value: Boolean) {
            savedState[KEY_PUBLIC] = value
            _uiState.update { it.copy(form = it.form.copy(isPublicProfile = value)) }
        }

        /** Never persisted — see the class KDoc. */
        fun onPasswordChange(value: String) = _uiState.update { it.copy(form = it.form.copy(newPassword = value)) }

        fun openGlyphPicker() = _uiState.update { it.copy(isPresentingGlyphPicker = true) }

        fun closeGlyphPicker() = _uiState.update { it.copy(isPresentingGlyphPicker = false) }

        fun onGlyphPicked(glyph: Glyph) =
            _uiState.update {
                it.copy(form = it.form.copy(pickedGlyph = glyph), isPresentingGlyphPicker = false)
            }

        fun onDismissRequested() {
            if (_uiState.value.isSaving) return
            effectsOut.emit(SettingsEffect.Dismiss)
        }

        // MARK: - Save

        fun save() {
            val state = _uiState.value
            if (!state.isDirty || state.isSaving) return
            _uiState.update { it.copy(isSaving = true, didSaveFail = false, handleErrorRes = null) }

            viewModelScope.launch {
                // Everything from the first write to the last, uninterruptibly:
                // there is no point in this sequence where stopping leaves the
                // account consistent.
                withContext(NonCancellable) { performSave(state) }
            }
        }

        private suspend fun performSave(state: SettingsUiState) {
            val initial = state.initial
            val form = state.form
            val trimmed = form.displayName.trim()
            val nameToSend = trimmed.takeIf { it != initial.displayName && it.isNotEmpty() }
            val glyphToSend = form.pickedGlyph?.takeIf { it.id != initial.glyphId }
            val passwordToSend = form.newPassword.takeIf { it.isNotEmpty() }

            // Handle first: claiming and going public in one save needs the
            // handle stored before the public_profile write passes the CHECK.
            var savedHandle = initial.handle
            if (state.normalizedHandle != (initial.handle ?: "")) {
                val claimed = state.normalizedHandle.takeIf { it.isNotEmpty() }
                val failure = runCatchingCancellable { profileService.setHandle(claimed) }.exceptionOrNull()
                if (failure != null) {
                    Log.e(TAG, "set_handle failed", failure)
                    val code = handleErrorStringRes(failure.toDataError())
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            handleErrorRes = code,
                            didSaveFail = code == null,
                        )
                    }
                    return
                }
                savedHandle = claimed
            }

            val rest =
                runCatchingCancellable {
                    profileService.saveSettings(
                        displayName = nameToSend,
                        glyphId = glyphToSend?.id,
                        password = passwordToSend,
                    )
                    // Releasing the handle already cleared the flag server-side,
                    // so only write the toggle while a handle exists.
                    if (form.isPublicProfile != initial.publicProfile && savedHandle != null) {
                        profileService.setPublicProfile(form.isPublicProfile)
                    }
                }
            rest.fold(
                onSuccess = {
                    // The password is gone from memory the moment the save lands;
                    // it was never in SavedStateHandle to begin with.
                    _uiState.update { it.copy(form = it.form.copy(newPassword = "")) }
                    effectsOut.emit(
                        SettingsEffect.Saved(
                            displayName = nameToSend ?: initial.displayName,
                            glyph = form.pickedGlyph,
                            handle = savedHandle,
                            isPublic = if (savedHandle == null) false else form.isPublicProfile,
                        ),
                    )
                },
                onFailure = {
                    Log.e(TAG, "settings save failed", it)
                    _uiState.update { state -> state.copy(isSaving = false, didSaveFail = true) }
                },
            )
        }

        fun dismissSaveError() = _uiState.update { it.copy(didSaveFail = false) }

        // MARK: - Deletion

        fun requestDelete() = _uiState.update { it.copy(deletion = DeletionState.CONFIRMING) }

        fun cancelDelete() = _uiState.update { it.copy(deletion = DeletionState.IDLE) }

        fun dismissDeleteError() = _uiState.update { it.copy(deletion = DeletionState.IDLE) }

        /**
         * Full erasure via the delete-account edge function (purge + storage +
         * auth user), then a local sign-out: the server session is already gone,
         * and `sessionStatus` dropping unmounts the authed NavHost to Welcome —
         * no navigation code needed here.
         *
         * `NonCancellable` for the same reason as the save, and more so: once the
         * account is purged server-side, a client that skipped its sign-out is
         * left holding a session token for a user that no longer exists.
         */
        fun confirmDelete() {
            if (_uiState.value.deletion == DeletionState.DELETING) return
            _uiState.update { it.copy(deletion = DeletionState.DELETING) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        profileService.deleteAccount()
                        supabase.signOut()
                    }.onFailure {
                        Log.e(TAG, "account deletion failed", it)
                        _uiState.update { state -> state.copy(deletion = DeletionState.FAILED) }
                    }
                }
            }
        }

        private companion object {
            const val KEY_NAME = "settings-display-name"
            const val KEY_HANDLE = "settings-handle"
            const val KEY_PUBLIC = "settings-public-profile"
        }
    }
