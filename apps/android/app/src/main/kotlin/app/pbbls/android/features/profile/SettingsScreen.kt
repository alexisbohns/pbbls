package app.pbbls.android.features.profile

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import app.pbbls.android.core.data.DeletionState
import app.pbbls.android.core.data.ReauthMethod
import app.pbbls.android.core.designsystem.ConfirmDeleteDialog
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesListDefaults
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesSectionHeader
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.ReauthDialog
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphPickerSheet
import app.pbbls.android.core.ui.GlyphPickerSlot
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onSaved: (displayName: String, glyph: Glyph?, handle: String?, isPublic: Boolean) -> Unit,
    glyphPicker: GlyphPickerSlot,
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
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
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
                LoadingIndicator()
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
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            // The row owns the toggle (Switch onCheckedChange = null), so
                            // TalkBack announces one "switch, on" node rather than two.
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_wallpaper_colors_title)) },
                                supportingContent = { Text(stringResource(R.string.settings_wallpaper_colors_body)) },
                                trailingContent = { Switch(checked = viewModel.useWallpaperColors, onCheckedChange = null) },
                                modifier =
                                    Modifier.toggleable(
                                        value = viewModel.useWallpaperColors,
                                        role = Role.Switch,
                                        onValueChange = viewModel::onUseWallpaperColorsChange,
                                    ),
                                colors = settingsRowColors(),
                            )
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
                val canGoPublic = uiState.initial.handle != null
                val publicProfileRows: List<@Composable () -> Unit> =
                    buildList {
                        add {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.settings_public_profile_toggle),
                                        // No handle yet: the whole row is disabled, not merely quiet.
                                        color = if (canGoPublic) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = uiState.form.isPublicProfile,
                                        onCheckedChange = null,
                                        enabled = canGoPublic,
                                    )
                                },
                                modifier =
                                    Modifier.toggleable(
                                        value = uiState.form.isPublicProfile,
                                        enabled = canGoPublic,
                                        role = Role.Switch,
                                        onValueChange = viewModel::onPublicProfileChange,
                                    ),
                                colors = settingsRowColors(),
                            )
                        }
                        uiState.shareUrl?.let { shareUrl ->
                            add {
                                SettingsNavRow(
                                    text = stringResource(R.string.settings_public_profile_share),
                                    onClick = { sharePublicProfile(context, shareUrl) },
                                )
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
                PebblesListSection(rows = publicProfileRows, rowPadding = PebblesListDefaults.ListItemRowPadding)
            }

            if (uiState.initial.providers.isNotEmpty()) {
                PebblesListSection(
                    header = stringResource(R.string.settings_providers_header),
                    rowPadding = PebblesListDefaults.ListItemRowPadding,
                    rows =
                        uiState.initial.providers.map { provider ->
                            {
                                // Brand names render verbatim — never localized.
                                ListItem(headlineContent = { Text(provider) }, colors = settingsRowColors())
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
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            SettingsNavRow(
                                text = stringResource(R.string.auth_consent_terms_link),
                                onClick = { openLegalDoc(context, LegalDoc.TERMS) },
                            )
                        },
                        {
                            SettingsNavRow(
                                text = stringResource(R.string.auth_consent_privacy_link),
                                onClick = { openLegalDoc(context, LegalDoc.PRIVACY) },
                            )
                        },
                    ),
            )

            // Art. 9 consent and the Art. 7(3) right to take it back (#972).
            // Health data only: terms, privacy and age assurance cannot be
            // withdrawn, so they never appear here as controls.
            PebblesListSection(
                header = stringResource(R.string.settings_consent_header),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            HealthConsentRow(
                                status = uiState.healthConsent,
                                canWithdraw = uiState.deletion == DeletionState.IDLE,
                                onWithdraw = viewModel::requestWithdrawConsent,
                            )
                        },
                    ),
            )

            // Store-mandated account deletion entry (Play hard blocker;
            // parity with iOS Settings → Account).
            PebblesListSection(
                header = stringResource(R.string.settings_account_header),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            val isWorking = uiState.signOutEverywhere == SignOutEverywhereState.WORKING
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_sign_out_everywhere)) },
                                trailingContent =
                                    if (isWorking) {
                                        { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                                    } else {
                                        null
                                    },
                                modifier = Modifier.clickable(enabled = !isWorking, onClick = viewModel::requestSignOutEverywhere),
                                colors = settingsRowColors(),
                            )
                        },
                        {
                            val isDeleting = uiState.deletion == DeletionState.DELETING
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_delete_account), color = colors.error) },
                                trailingContent =
                                    if (isDeleting) {
                                        {
                                            LoadingIndicator(
                                                color = colors.error,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                modifier = Modifier.clickable(enabled = !isDeleting, onClick = viewModel::requestDelete),
                                colors = settingsRowColors(),
                            )
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

/** Rows sit on the section's outlined card, so they take its container rather than painting their own. */
@Composable
internal fun settingsRowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A settings row that opens something: headline + trailing chevron, one full-width target. */
@Composable
internal fun SettingsNavRow(
    text: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

/**
 * The health-data consent row (#972, mirrors web's `ConsentSection`): what was
 * given, when, and against which policy version, with Withdraw as the one
 * action. Withdraw opens the deletion dialog, never a toggle: the consent is
 * the lawful basis for the journal, so taking it back closes the account.
 */
@Composable
internal fun HealthConsentRow(
    status: HealthConsentStatus,
    canWithdraw: Boolean,
    onWithdraw: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val supporting =
        when (status) {
            HealthConsentStatus.Loading -> null
            is HealthConsentStatus.Given -> {
                val date =
                    remember(status.consent.grantedAt, locale) {
                        status.consent.grantedAt
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                    }
                stringResource(R.string.settings_consent_given_on, date, status.consent.documentVersion)
            }
            HealthConsentStatus.NotRecorded -> stringResource(R.string.settings_consent_not_recorded)
            HealthConsentStatus.Unavailable -> stringResource(R.string.settings_consent_unavailable)
        }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_consent_health_data)) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent =
            if (status is HealthConsentStatus.Given) {
                {
                    TextButton(onClick = onWithdraw, enabled = canWithdraw) {
                        Text(stringResource(R.string.settings_consent_withdraw))
                    }
                }
            } else {
                null
            },
        colors = settingsRowColors(),
    )
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
