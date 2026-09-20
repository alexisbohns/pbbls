package app.pbbls.android.features.profile

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.components.DashedPlaceholder
import app.pbbls.android.features.glyph.models.SystemGlyph
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.ui.ObserveUiEffects

/**
 * Create/edit form for a soul — merges iOS `CreateSoulSheet` + `EditSoulSheet`
 * (which differ only in initial state and the write call) into one full-screen
 * surface (D5): name field + glyph row → [GlyphPickerSheet] (the M39 D12
 * parked glyph slot, landing here per D8). [soulId] `null` means create — the
 * glyph defaults to [SystemGlyph.DEFAULT] and [SoulFormViewModel] fetches its
 * strokes for the thumbnail; a non-null id is fetched by the ViewModel itself
 * (#852), so this screen never receives the row from its caller.
 * Writes are direct RLS-scoped single-table calls (D6); `souls_glyph_usable`
 * enforces glyph ownership server-side.
 *
 * Deviation from iOS: the picker already returns the full `Glyph`, so the
 * post-pick thumbnail refetch iOS carries ("tracked separately" in its
 * comments) is dropped rather than ported.
 */
@Composable
fun SoulFormScreen(
    soulId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoulFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    // `start` is guarded, so a rotation cannot re-seed over the user's edits.
    LaunchedEffect(soulId) { viewModel.start(soulId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            SoulFormEffect.Saved -> onSaved()
            SoulFormEffect.Dismiss -> onDismiss()
        }
    }

    PebblesScreen(
        modifier = modifier.background(system.background),
        topBar = {
            PebblesTopBar(
                title = stringResource(uiState.titleRes),
                leading = {
                    PebblesTopBarTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = viewModel::onDismissRequested,
                    )
                },
                trailing = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = PebblesTheme.colors.accent.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::save,
                            enabled = uiState.canSave,
                            color = if (uiState.canSave) system.secondary else system.muted,
                        )
                    }
                },
            )
        },
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
            }
            return@PebblesScreen
        }

        val loadErrorRes = uiState.loadErrorRes
        if (loadErrorRes != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                PebblesText(
                    text = stringResource(loadErrorRes),
                    style = PebblesTypography.body,
                    color = PebblesDestructive,
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
                    .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            PebblesListSection(
                rows =
                    listOf(
                        {
                            BasicTextField(
                                value = uiState.name,
                                onValueChange = viewModel::onNameChange,
                                singleLine = true,
                                textStyle = PebblesTypography.body.copy(color = system.foreground),
                                cursorBrush = SolidColor(PebblesTheme.colors.accent.primary),
                                keyboardOptions =
                                    KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                decorationBox = { inner ->
                                    if (uiState.name.isEmpty()) {
                                        PebblesText(
                                            text = stringResource(R.string.create_soul_name_placeholder),
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

            PebblesListSection(
                header = stringResource(R.string.create_glyph_header),
                rows =
                    listOf(
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = viewModel::openPicker),
                            ) {
                                val glyph = uiState.glyph
                                if (glyph != null) {
                                    GlyphView(
                                        case = GlyphViewCase.DEFAULT,
                                        strokes = glyph.strokes,
                                        viewBox = glyph.viewBox,
                                        side = 32.dp,
                                    )
                                } else {
                                    DashedPlaceholder()
                                }
                                PebblesText(
                                    text = stringResource(R.string.soul_form_glyph_choose),
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

            if (uiState.didSaveFail) {
                PebblesText(
                    text = stringResource(uiState.saveErrorRes),
                    style = PebblesTypography.subhead,
                    color = PebblesDestructive,
                )
            }
        }
    }

    if (uiState.isPresentingPicker) {
        GlyphPickerSheet(
            currentGlyphId = uiState.glyphId,
            onDismiss = viewModel::closePicker,
            onSelected = viewModel::onGlyphPicked,
        )
    }
}

/**
 * Pure save gate — merges `SoulDraft.isValid` with `EditSoulSheet.canSave`:
 * the trimmed name must be non-empty, and in edit mode something must have
 * changed (name or glyph). Create mode ([originalName] null) needs validity only.
 */
internal fun soulFormCanSave(
    originalName: String?,
    originalGlyphId: String?,
    name: String,
    glyphId: String,
): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    if (originalName == null) return true
    return trimmed != originalName || glyphId != originalGlyphId
}
