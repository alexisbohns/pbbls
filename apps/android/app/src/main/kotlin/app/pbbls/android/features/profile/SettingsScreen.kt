package app.pbbls.android.features.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ReauthMethod
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.ReauthDialog
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphPickerSheet
import app.pbbls.android.core.ui.GlyphPickerSlot

private const val TAG = "settings"

/**
 * Profile settings — ports iOS `SettingsSheet.swift` as a full-screen surface
 * (D5: never stack sheets; the glyph picker is this screen's single
 * ModalBottomSheet level). Sections depend on the account type: SSO accounts
 * see a read-only Providers list (text-only brand labels, risk-4 v1), email
 * accounts a new-password field. Save sends only changed fields —
 * `update_profile` (null = keep; cannot clear glyph_id by design) then the
 * GoTrue password update — and stays open with an inline error on failure.
 *
 * The profile, its glyph, and the account's email/providers are fetched by
 * [SettingsViewModel] itself (#852) rather than handed down by
 * `ProfileScreen` — `SettingsKey` carries no argument to seed from.
 *
 * @param glyphPicker The glyph picker's body. It is glyph's, so the entry
 *   provider hands it in rather than this screen importing it (#914).
 * @param onSignOut The app's sign-out, the one You's Log out calls. Log out is
 *   the last row of the page, in a group of its own (#1383 D14).
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onSaved: (displayName: String, glyph: Glyph?, handle: String?, isPublic: Boolean) -> Unit,
    glyphPicker: GlyphPickerSlot,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is SettingsEffect.Saved ->
                onSaved(effect.displayName, effect.glyph, effect.handle, effect.isPublic)

            SettingsEffect.Dismiss -> onDismiss()
        }
    }

    SettingsContent(
        state = uiState,
        device =
            SettingsDeviceChoices(
                useWallpaperColors = viewModel.useWallpaperColors,
                hapticsOn = viewModel.hapticsOn,
                shortcutsLayout = viewModel.shortcutsLayout,
                shortcutsStones = viewModel.shortcutsStones,
            ),
        actions =
            remember(viewModel, onSignOut) {
                SettingsActions(
                    onBack = viewModel::onDismissRequested,
                    onSave = viewModel::save,
                    onOpenGlyphPicker = viewModel::openGlyphPicker,
                    onUseWallpaperColorsChange = viewModel::onUseWallpaperColorsChange,
                    onShortcutsLayoutChange = viewModel::onShortcutsLayoutChange,
                    onShortcutsStonesChange = viewModel::onShortcutsStonesChange,
                    onHapticsChange = viewModel::onHapticsChange,
                    onDisplayNameChange = viewModel::onDisplayNameChange,
                    onHandleChange = viewModel::onHandleChange,
                    onPublicProfileChange = viewModel::onPublicProfileChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onWithdrawConsent = viewModel::requestWithdrawConsent,
                    onSignOutEverywhere = viewModel::requestSignOutEverywhere,
                    onDeleteAccount = viewModel::requestDelete,
                    onLogOut = onSignOut,
                )
            },
        modifier = modifier,
    )

    if (uiState.deletion == DeletionState.CONFIRMING) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_delete_account_title),
            message = stringResource(R.string.settings_delete_account_message),
            confirmText = stringResource(R.string.settings_delete_account_confirm),
            onConfirm = {
                viewModel.confirmDelete()
            },
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (uiState.deletion == DeletionState.CONFIRMING_WITHDRAWAL) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_consent_withdraw_title),
            message = stringResource(R.string.settings_consent_withdraw_message),
            confirmText = stringResource(R.string.settings_consent_withdraw_confirm),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (uiState.deletion == DeletionState.FAILED) {
        DeleteErrorDialog(
            message = stringResource(R.string.settings_delete_account_error),
            onDismiss = viewModel::dismissDeleteError,
        )
    }
    // The delete row's spinner stays keyed on DELETING: while re-authenticating,
    // this dialog is the feedback.
    uiState.reauth?.let { reauth ->
        ReauthDialog(
            usesPassword = reauth.method == ReauthMethod.PASSWORD,
            password = reauth.password,
            onPasswordChange = viewModel::onReauthPasswordChange,
            isWorking = reauth.isWorking,
            errorText = reauth.errorRes?.let { stringResource(it) },
            onConfirm = viewModel::submitReauth,
            onDismiss = viewModel::cancelReauth,
        )
    }

    if (uiState.signOutEverywhere == SignOutEverywhereState.CONFIRMING) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_sign_out_everywhere_title),
            message = stringResource(R.string.settings_sign_out_everywhere_message),
            confirmText = stringResource(R.string.settings_sign_out_everywhere_confirm),
            onConfirm = viewModel::confirmSignOutEverywhere,
            onDismiss = viewModel::cancelSignOutEverywhere,
        )
    }
    if (uiState.signOutEverywhere == SignOutEverywhereState.FAILED) {
        DeleteErrorDialog(
            message = stringResource(R.string.settings_sign_out_everywhere_error),
            onDismiss = viewModel::dismissSignOutEverywhereError,
        )
    }

    if (uiState.isPresentingGlyphPicker) {
        GlyphPickerSheet(
            currentGlyphId = uiState.form.pickedGlyph?.id ?: uiState.initial.glyphId,
            onDismiss = viewModel::closeGlyphPicker,
            onSelected = { glyph ->
                viewModel.onGlyphPicked(glyph)
            },
            picker = glyphPicker,
        )
    }
}

/**
 * Pure dirty-check — mirrors `SettingsSheet.isDirty`: a trimmed, non-empty
 * name change, a different picked glyph, a non-empty new password, a changed
 * handle (compared normalized, as the DB stores it), or a flipped public flag.
 */
internal fun settingsIsDirty(
    initialName: String,
    name: String,
    initialGlyphId: String?,
    pickedGlyphId: String?,
    newPassword: String,
    initialHandle: String? = null,
    handle: String = initialHandle ?: "",
    initialPublicProfile: Boolean = false,
    isPublicProfile: Boolean = initialPublicProfile,
): Boolean {
    val trimmed = name.trim()
    val nameChanged = trimmed != initialName && trimmed.isNotEmpty()
    val glyphChanged = pickedGlyphId != null && pickedGlyphId != initialGlyphId
    val handleChanged = handle.trim().lowercase() != (initialHandle ?: "")
    val publicChanged = isPublicProfile != initialPublicProfile
    return nameChanged || glyphChanged || newPassword.isNotEmpty() || handleChanged || publicChanged
}

/**
 * Maps a `set_handle` rejection to its inline string resource. The RPC raises
 * stable codes; anything else (`not_found`, a dropped connection) is not a
 * verdict on the handle, so it returns null and the caller falls back to the
 * generic save error.
 *
 * Takes the decoded [DataError] rather than the `Throwable` (#850): it used to
 * scan `error.message`, which is a blob containing the request URL and headers
 * as well as the condition. Only a deliberately raised condition is a verdict
 * on the handle, so anything that is not a [DataError.Conflict] returns null.
 */
internal fun handleErrorStringRes(error: DataError): Int? {
    val conflict = error as? DataError.Conflict ?: return null
    return when (conflict.code) {
        "handle_taken" -> R.string.settings_handle_error_taken
        "handle_reserved" -> R.string.settings_handle_error_reserved
        "invalid_handle" -> R.string.settings_handle_error_invalid
        else -> null
    }
}
