package app.pbbls.android.features.profile

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.LegalDoc
import app.pbbls.android.components.openLegalDoc
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "settings"

/**
 * Profile settings — ports iOS `SettingsSheet.swift` as a full-screen surface
 * (D5: never stack sheets; the glyph picker is this screen's single
 * ModalBottomSheet level). Sections depend on the account type: SSO accounts
 * see a read-only Providers list (text-only brand labels, risk-4 v1), email
 * accounts a new-password field. Save sends only changed fields —
 * `update_profile` (null = keep; cannot clear glyph_id by design) then the
 * GoTrue password update — and stays open with an inline error on failure.
 */
@Composable
fun SettingsScreen(
    initialDisplayName: String,
    initialGlyphId: String?,
    initialGlyphStrokes: List<GlyphStroke>?,
    email: String?,
    providers: List<String>,
    onDismiss: () -> Unit,
    onSaved: (displayName: String, glyph: Glyph?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileService = LocalProfileService.current
    val system = PebblesTheme.colors.system
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(initialDisplayName) }
    var pickedGlyph by remember { mutableStateOf<Glyph?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }
    var isPresentingGlyphPicker by remember { mutableStateOf(false) }

    val isDirty =
        settingsIsDirty(
            initialName = initialDisplayName,
            name = displayName,
            initialGlyphId = initialGlyphId,
            pickedGlyphId = pickedGlyph?.id,
            newPassword = newPassword,
        )
    val currentStrokes = pickedGlyph?.strokes ?: initialGlyphStrokes

    BackHandler(enabled = !isSaving) { onDismiss() }

    fun save() {
        if (!isDirty || isSaving) return
        scope.launch {
            isSaving = true
            showSaveError = false
            val trimmed = displayName.trim()
            val nameToSend = trimmed.takeIf { it != initialDisplayName && it.isNotEmpty() }
            val glyphToSend = pickedGlyph?.takeIf { it.id != initialGlyphId }
            val passwordToSend = newPassword.takeIf { it.isNotEmpty() }
            try {
                profileService.saveSettings(
                    displayName = nameToSend,
                    glyphId = glyphToSend?.id,
                    password = passwordToSend,
                )
                onSaved(nameToSend ?: initialDisplayName, pickedGlyph)
            } catch (e: Exception) {
                Log.e(TAG, "settings save failed", e)
                showSaveError = true
                isSaving = false
            }
        }
    }

    PebblesScreen(
        modifier = modifier.background(system.background),
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.settings_title),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { if (!isSaving) onDismiss() },
                    )
                },
                trailing = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = PebblesTheme.colors.accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = { save() },
                            enabled = isDirty,
                            color = if (isDirty) system.secondary else system.muted,
                        )
                    }
                },
            )
        },
    ) {
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
                val hasStrokes = !currentStrokes.isNullOrEmpty()
                GlyphView(
                    case = if (hasStrokes) GlyphViewCase.PROFILE else GlyphViewCase.CARVE,
                    strokes = currentStrokes,
                    side = 120.dp,
                    modifier = Modifier.clickable { isPresentingGlyphPicker = true },
                )
            }

            PebblesListSection(
                header = stringResource(R.string.settings_informations_header),
                rows =
                    listOf(
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PebblesText(
                                    text = stringResource(R.string.settings_name_label),
                                    style = PebblesTypography.body,
                                    color = system.secondary,
                                )
                                Spacer(Modifier.weight(1f))
                                BasicTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it },
                                    singleLine = true,
                                    textStyle =
                                        PebblesTypography.body.copy(
                                            color = system.foreground,
                                            textAlign = TextAlign.End,
                                        ),
                                    cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                                    keyboardOptions =
                                        KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    decorationBox = { inner ->
                                        if (displayName.isEmpty()) {
                                            PebblesText(
                                                text = stringResource(R.string.settings_name_placeholder),
                                                style = PebblesTypography.body,
                                                color = system.muted,
                                            )
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(2f),
                                )
                            }
                        },
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PebblesText(
                                    text = stringResource(R.string.settings_email_label),
                                    style = PebblesTypography.body,
                                    color = system.secondary,
                                )
                                Spacer(Modifier.weight(1f))
                                PebblesText(
                                    text = email ?: "—",
                                    style = PebblesTypography.body,
                                    color = system.secondary,
                                    maxLines = 1,
                                )
                            }
                        },
                    ),
            )

            if (providers.isNotEmpty()) {
                PebblesListSection(
                    header = stringResource(R.string.settings_providers_header),
                    rows =
                        providers.map { provider ->
                            {
                                // Brand names render verbatim — never localized.
                                PebblesText(
                                    text = provider,
                                    style = PebblesTypography.body,
                                    color = system.foreground,
                                )
                            }
                        },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm)) {
                    PebblesListSection(
                        header = stringResource(R.string.settings_password_header),
                        rows =
                            listOf(
                                {
                                    BasicTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        textStyle = PebblesTypography.body.copy(color = system.foreground),
                                        cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                                        decorationBox = { inner ->
                                            if (newPassword.isEmpty()) {
                                                PebblesText(
                                                    text = stringResource(R.string.settings_password_placeholder),
                                                    style = PebblesTypography.body,
                                                    color = system.muted,
                                                )
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                            ),
                    )
                    PebblesText(
                        text = stringResource(R.string.settings_password_footer),
                        style = PebblesTypography.subhead,
                        color = system.secondary,
                    )
                }
            }

            if (showSaveError) {
                PebblesText(
                    text = stringResource(R.string.settings_save_error),
                    style = PebblesTypography.subhead,
                    color = PebblesDestructive,
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
                                PebblesText(
                                    text = stringResource(R.string.auth_consent_terms_link),
                                    style = PebblesTypography.body,
                                    color = system.foreground,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = system.secondary,
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
                                PebblesText(
                                    text = stringResource(R.string.auth_consent_privacy_link),
                                    style = PebblesTypography.body,
                                    color = system.foreground,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = system.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    ),
            )
        }
    }

    if (isPresentingGlyphPicker) {
        GlyphPickerSheet(
            currentGlyphId = pickedGlyph?.id ?: initialGlyphId,
            onDismiss = { isPresentingGlyphPicker = false },
            onSelected = { glyph ->
                pickedGlyph = glyph
                isPresentingGlyphPicker = false
            },
        )
    }
}

/**
 * Pure dirty-check — mirrors `SettingsSheet.isDirty`: a trimmed, non-empty
 * name change, a different picked glyph, or a non-empty new password.
 */
internal fun settingsIsDirty(
    initialName: String,
    name: String,
    initialGlyphId: String?,
    pickedGlyphId: String?,
    newPassword: String,
): Boolean {
    val trimmed = name.trim()
    val nameChanged = trimmed != initialName && trimmed.isNotEmpty()
    val glyphChanged = pickedGlyphId != null && pickedGlyphId != initialGlyphId
    return nameChanged || glyphChanged || newPassword.isNotEmpty()
}
