package app.pbbls.android.features.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.DataError
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesSectionHeader
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet

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
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onSaved: (displayName: String, glyph: Glyph?, handle: String?, isPublic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is SettingsEffect.Saved ->
                onSaved(effect.displayName, effect.glyph, effect.handle, effect.isPublic)

            SettingsEffect.Dismiss -> onDismiss()
        }
    }

    PebblesScreen(
        modifier = modifier.background(colors.surface),
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.settings_title),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = viewModel::onDismissRequested,
                    )
                },
                trailing = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = colors.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::save,
                            enabled = uiState.isDirty,
                            color = if (uiState.isDirty) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
                        )
                    }
                },
            )
        },
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return@PebblesScreen
        }

        val loadErrorRes = uiState.loadErrorRes
        if (loadErrorRes != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(loadErrorRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
            }
            return@PebblesScreen
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            // Header glyph — tap opens the picker (the SettingsSheet 120pt header).
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val hasStrokes = !uiState.currentStrokes.isNullOrEmpty()
                GlyphView(
                    case = if (hasStrokes) GlyphViewCase.PROFILE else GlyphViewCase.CARVE,
                    strokes = uiState.currentStrokes,
                    side = 120.dp,
                    modifier = Modifier.clickable(onClick = viewModel::openGlyphPicker),
                )
            }

            PebblesListSection(
                header = stringResource(R.string.settings_appearance_section),
                rows =
                    listOf(
                        {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = viewModel.useWallpaperColors,
                                            role = Role.Switch,
                                            onValueChange = viewModel::onUseWallpaperColorsChange,
                                        ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_wallpaper_colors_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        stringResource(R.string.settings_wallpaper_colors_body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                // null onCheckedChange: the Row owns the toggle, so
                                // TalkBack announces one "switch, on" node rather than two.
                                Switch(checked = viewModel.useWallpaperColors, onCheckedChange = null)
                            }
                        },
                    ),
            )

            // Editable fields are stock outlined fields under the section header (#854);
            // the bordered list keeps only rows that are not text input.
            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                PebblesSectionHeader(text = stringResource(R.string.settings_informations_header))
                OutlinedTextField(
                    value = uiState.form.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.settings_name_label)) },
                    placeholder = { Text(stringResource(R.string.settings_name_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.initial.email ?: "—",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Public profile (M50): claim a handle, opt in, then share the link.
            Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                val publicProfileRows: List<@Composable () -> Unit> =
                    buildList {
                        add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = uiState.initial.handle != null,
                                            onClick = viewModel::togglePublicProfile,
                                        ),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_public_profile_toggle),
                                    style = MaterialTheme.typography.bodyLarge,
                                    // No handle yet: the whole row is disabled, not merely quiet.
                                    color = if (uiState.initial.handle != null) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                                )
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = uiState.form.isPublicProfile,
                                    onCheckedChange = viewModel::onPublicProfileChange,
                                    enabled = uiState.initial.handle != null,
                                )
                            }
                        }
                        uiState.shareUrl?.let { shareUrl ->
                            add {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { sharePublicProfile(context, shareUrl) },
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_public_profile_share),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                PebblesSectionHeader(text = stringResource(R.string.settings_public_profile_header))
                OutlinedTextField(
                    value = uiState.form.handle,
                    onValueChange = viewModel::onHandleChange,
                    label = { Text(stringResource(R.string.settings_handle_label)) },
                    placeholder = { Text(stringResource(R.string.settings_handle_placeholder)) },
                    prefix = { Text("@") },
                    isError = uiState.handleErrorRes != null,
                    supportingText = {
                        Text(
                            uiState.handleErrorRes?.let { stringResource(it) }
                                ?: if (uiState.initial.handle == null) {
                                    stringResource(R.string.settings_public_profile_needs_handle)
                                } else {
                                    stringResource(R.string.settings_handle_footer)
                                },
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                PebblesListSection(rows = publicProfileRows)
            }

            if (uiState.initial.providers.isNotEmpty()) {
                PebblesListSection(
                    header = stringResource(R.string.settings_providers_header),
                    rows =
                        uiState.initial.providers.map { provider ->
                            {
                                // Brand names render verbatim — never localized.
                                Text(
                                    text = provider,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                )
                            }
                        },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                    PebblesSectionHeader(text = stringResource(R.string.settings_password_header))
                    OutlinedTextField(
                        value = uiState.form.newPassword,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(stringResource(R.string.settings_password_placeholder)) },
                        supportingText = { Text(stringResource(R.string.settings_password_footer)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (uiState.didSaveFail) {
                Text(
                    text = stringResource(R.string.settings_save_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }

            PebblesListSection(
                header = stringResource(R.string.settings_legal_header),
                rows =
                    listOf(
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openLegalDoc(context, LegalDoc.TERMS) },
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_consent_terms_link),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openLegalDoc(context, LegalDoc.PRIVACY) },
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_consent_privacy_link),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    ),
            )

            // Store-mandated account deletion entry (Play hard blocker;
            // parity with iOS Settings → Account).
            PebblesListSection(
                header = stringResource(R.string.settings_account_header),
                rows =
                    listOf(
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = uiState.deletion != DeletionState.DELETING,
                                            onClick = viewModel::requestDelete,
                                        ),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_delete_account),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.error,
                                )
                                Spacer(Modifier.weight(1f))
                                if (uiState.deletion == DeletionState.DELETING) {
                                    CircularProgressIndicator(
                                        color = colors.error,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    ),
            )
        }
    }

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
    if (uiState.deletion == DeletionState.FAILED) {
        DeleteErrorDialog(
            message = stringResource(R.string.settings_delete_account_error),
            onDismiss = viewModel::dismissDeleteError,
        )
    }

    if (uiState.isPresentingGlyphPicker) {
        GlyphPickerSheet(
            currentGlyphId = uiState.form.pickedGlyph?.id ?: uiState.initial.glyphId,
            onDismiss = viewModel::closeGlyphPicker,
            onSelected = { glyph ->
                viewModel.onGlyphPicked(glyph)
            },
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

/**
 * Android's share affordance for the public profile — the app's first
 * `ACTION_SEND` chooser (iOS uses `ShareLink`).
 */
private fun sharePublicProfile(
    context: Context,
    url: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    context.startActivity(Intent.createChooser(intent, null))
}
