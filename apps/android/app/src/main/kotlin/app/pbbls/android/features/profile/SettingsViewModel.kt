package app.pbbls.android.features.profile

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AccountDeletionFlow
import app.pbbls.android.core.data.AccountDeletionUi
import app.pbbls.android.core.data.AppearancePreferences
import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.core.data.ProfileServicing
import app.pbbls.android.core.data.ReauthPurpose
import app.pbbls.android.core.data.ReauthUi
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.isReauthRequired
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.HealthDataConsent
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
 * The health-data consent row (#972). Its load failing is not the screen's
 * failure, so it has a state of its own rather than riding on `loadErrorRes`.
 */
sealed interface HealthConsentStatus {
    data object Loading : HealthConsentStatus

    data class Given(
        val consent: HealthDataConsent,
    ) : HealthConsentStatus

    data object NotRecorded : HealthConsentStatus

    data object Unavailable : HealthConsentStatus
}

/** "Sign out of all devices": ask, run, or report that the server never heard. */
enum class SignOutEverywhereState { IDLE, CONFIRMING, WORKING, FAILED }

data class SettingsUiState(
    val initial: SettingsInitial = SettingsInitial(),
    val form: SettingsForm = SettingsForm(),
    val isSaving: Boolean = false,
    @StringRes val handleErrorRes: Int? = null,
    val didSaveFail: Boolean = false,
    val isPresentingGlyphPicker: Boolean = false,
    val deletion: DeletionState = DeletionState.IDLE,
    val healthConsent: HealthConsentStatus = HealthConsentStatus.Loading,
    val signOutEverywhere: SignOutEverywhereState = SignOutEverywhereState.IDLE,
    val reauth: ReauthUi? = null,
    /** True while the ViewModel fetches its own profile — see the class KDoc. */
    val isLoading: Boolean = true,
    /** Set when that fetch fails — the screen shows this instead of an empty form. */
    @StringRes val loadErrorRes: Int? = null,
) {
    /** Handles are stored normalized (DB CHECK), so compare and write that form. */
    val normalizedHandle: String
        get() = form.handle.trim().lowercase()

    val isDirty: Boolean
        get() =
            !isLoading &&
                loadErrorRes == null &&
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

    /**
     * The save carries a gated change (#976): a new password, or turning the
     * public profile on. Turning it off, or any other field, never asks.
     */
    val saveNeedsRecentAuth: Boolean
        get() =
            form.newPassword.isNotEmpty() ||
                (!initial.publicProfile && form.isPublicProfile && normalizedHandle.isNotEmpty())

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
 * State holder for Settings (#849, #852).
 *
 * **This ViewModel loads its own profile.** It used to receive seven initial
 * values from `ProfileScreen`, which had already loaded them — but `SettingsKey`
 * carries no argument (there is nothing to seed from, unlike the soul/collection
 * forms' id), and as a nav entry Settings has no parent to hand it anything. So
 * it fetches the profile and the glyph strokes itself (mirrors
 * [ProfileViewModel.fetch]), plus the email and linked providers off the
 * session (mirrors [ProfileViewModel.publish]). Unlike [SoulFormViewModel]'s
 * `start(id)`, there is no id to guard against re-seeding on: [init] runs
 * exactly once per instance, so a private, ungated [load] is enough — this
 * cover's ViewModel is scoped to Profile's back stack entry the same way
 * theirs are.
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
        private val appearance: AppearancePreferences,
        private val consents: ConsentServicing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<SettingsEffect>(viewModelScope)
        val effects: Flow<SettingsEffect> = effectsOut.flow

        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        /**
         * Device-local and applied instantly, so it bypasses the form and the
         * dirty/save cycle — there is nothing to send to the server (#853).
         */
        val useWallpaperColors: Boolean
            get() = appearance.useWallpaperColors

        fun onUseWallpaperColorsChange(value: Boolean) {
            appearance.setUseWallpaperColors(value)
        }

        init {
            load()
            loadHealthConsent()
        }

        /**
         * Fetches the profile row, its glyph strokes, and the session identity,
         * then seeds the form from whatever survived process death.
         *
         * The glyph fetch failing is decoration, not the screen (mirrors
         * [ProfileViewModel.fetch]): the header just renders without it. The
         * profile fetch failing IS the screen — [SettingsUiState.loadErrorRes]
         * — since there is nothing else to show a form for.
         */
        private fun load() {
            viewModelScope.launch {
                runCatchingCancellable { profileService.loadProfile() }
                    .fold(
                        onSuccess = { onProfileLoaded(it) },
                        onFailure = {
                            Log.e(TAG, "settings load failed", it)
                            _uiState.update { state ->
                                state.copy(isLoading = false, loadErrorRes = R.string.settings_load_error)
                            }
                        },
                    )
            }
        }

        /**
         * The live health-data grant, for the Consent row (#972). Separate from
         * [load]: a failed ledger read leaves the rest of Settings usable, and
         * account deletion is still reachable from the Account section.
         */
        private fun loadHealthConsent() {
            viewModelScope.launch {
                val status =
                    runCatchingCancellable { consents.healthData() }.fold(
                        onSuccess = { it?.let(HealthConsentStatus::Given) ?: HealthConsentStatus.NotRecorded },
                        onFailure = {
                            Log.e(TAG, "health consent load failed", it)
                            HealthConsentStatus.Unavailable
                        },
                    )
                _uiState.update { it.copy(healthConsent = status) }
            }
        }

        private suspend fun onProfileLoaded(profile: ProfileRow) {
            val glyphStrokes =
                profile.glyphId?.let { id ->
                    runCatchingCancellable { profileService.loadGlyphStrokes(id) }
                        .onFailure { Log.e(TAG, "glyph fetch failed", it) }
                        .getOrNull()
                }
            val initial =
                SettingsInitial(
                    displayName = profile.displayName.orEmpty(),
                    glyphId = profile.glyphId,
                    glyphStrokes = glyphStrokes,
                    handle = profile.handle,
                    publicProfile = profile.publicProfile,
                    email = supabase.session?.user?.email,
                    providers =
                        linkedProviders(
                            supabase.session
                                ?.user
                                ?.identities
                                ?.map { it.provider },
                        ),
                )
            _uiState.update {
                it.copy(
                    initial = initial,
                    isLoading = false,
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
            if (!state.isDirty || state.isSaving || state.reauth != null) return
            if (state.saveNeedsRecentAuth && !isSignInFresh()) {
                startReauth(ReauthPurpose.SAVE)
                return
            }
            runSave(state)
        }

        private fun runSave(state: SettingsUiState) {
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
                    // The password has landed: clear it now, not on overall
                    // success. A later step can fail (the public write's
                    // reauth_required re-runs the whole save from the form), and
                    // re-sending an applied password is GoTrue's 422
                    // same_password, which would fail the save for good.
                    if (passwordToSend != null) {
                        _uiState.update { it.copy(form = it.form.copy(newPassword = "")) }
                    }
                    // Releasing the handle already cleared the flag server-side,
                    // so only write the toggle while a handle exists.
                    if (form.isPublicProfile != initial.publicProfile && savedHandle != null) {
                        profileService.setPublicProfile(form.isPublicProfile)
                    }
                }
            rest.fold(
                onSuccess = {
                    // The password left memory as soon as it landed (above); it
                    // was never in SavedStateHandle to begin with.
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
                    if (it.isReauthRequired()) {
                        // The server's clock or the #977 switch disagrees with ours.
                        // The handle (if any) is already stored and re-sending it
                        // is a no-op, and a landed password was cleared from the
                        // form, so re-running the whole save after the re-auth
                        // is safe.
                        _uiState.update { state -> state.copy(isSaving = false) }
                        startReauth(ReauthPurpose.SAVE)
                    } else {
                        _uiState.update { state -> state.copy(isSaving = false, didSaveFail = true) }
                    }
                },
            )
        }

        fun dismissSaveError() = _uiState.update { it.copy(didSaveFail = false) }

        // MARK: - Sign out everywhere (#976)

        fun requestSignOutEverywhere() = _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.CONFIRMING) }

        fun cancelSignOutEverywhere() = _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.IDLE) }

        fun dismissSignOutEverywhereError() = _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.IDLE) }

        /**
         * Revokes every session the user holds, this one included. On success
         * the session drops and the authed NavHost unmounts to Welcome, so
         * there is nothing to navigate. Not gated by a recent sign-in: it only
         * ever takes access away.
         */
        fun confirmSignOutEverywhere() {
            if (_uiState.value.signOutEverywhere == SignOutEverywhereState.WORKING) return
            _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.WORKING) }
            viewModelScope.launch {
                runCatchingCancellable { supabase.signOut(everywhere = true) }
                    .onFailure {
                        Log.e(TAG, "global sign-out failed", it)
                        _uiState.update { state -> state.copy(signOutEverywhere = SignOutEverywhereState.FAILED) }
                    }
            }
        }

        // MARK: - Deletion

        /**
         * Deletion and the recent sign-in in front of it are shared with the
         * consent gate (#1030), so they live in [AccountDeletionFlow]. Its
         * slice of state is this screen's `deletion` and `reauth`, and the
         * save's re-auth comes back through `onReauthenticated`.
         */
        private val account =
            AccountDeletionFlow(
                scope = viewModelScope,
                profileService = profileService,
                supabase = supabase,
                read = { _uiState.value.let { AccountDeletionUi(it.deletion, it.reauth) } },
                write = { change ->
                    _uiState.update { state ->
                        val next = change(AccountDeletionUi(state.deletion, state.reauth))
                        state.copy(deletion = next.deletion, reauth = next.reauth)
                    }
                },
                // Re-reads the current form, so edits made while the dialog
                // was up are included.
                onReauthenticated = { purpose -> if (purpose == ReauthPurpose.SAVE) runSave(_uiState.value) },
            )

        fun requestDelete() = account.request()

        /**
         * Withdrawing the health-data consent is deleting the account (#972,
         * mirrors web's `ConsentSection`): the consent is the lawful basis for
         * the journal itself, so there is no Pebbles that keeps it and stops
         * processing it. Only the dialog differs; [confirmDelete] runs the same
         * recent sign-in check and the same erasure. `withdraw_consent` is not
         * called first, since the purge removes the ledger row with the account.
         */
        fun requestWithdrawConsent() = account.requestWithdrawal()

        fun cancelDelete() = account.cancel()

        fun dismissDeleteError() = account.dismissError()

        fun confirmDelete() = account.confirm()

        // MARK: - Recent sign-in (#976)

        private fun isSignInFresh(): Boolean = account.isSignInFresh()

        private fun startReauth(purpose: ReauthPurpose) = account.startReauth(purpose)

        fun onReauthPasswordChange(value: String) = account.onReauthPasswordChange(value)

        fun cancelReauth() = account.cancelReauth()

        fun submitReauth() = account.submitReauth()

        private companion object {
            const val KEY_NAME = "settings-display-name"
            const val KEY_HANDLE = "settings-handle"
            const val KEY_PUBLIC = "settings-public-profile"
        }
    }
