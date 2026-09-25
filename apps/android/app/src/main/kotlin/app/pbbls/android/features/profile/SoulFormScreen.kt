package app.pbbls.android.features.profile

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.designsystem.DashedPlaceholder
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesListDefaults
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.model.SystemGlyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.path.create.pickers.GlyphPickerSheet

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoulFormScreen(
    soulId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoulFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    // `start` is guarded, so a rotation cannot re-seed over the user's edits.
    LaunchedEffect(soulId) { viewModel.start(soulId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            SoulFormEffect.Saved -> onSaved()
            SoulFormEffect.Dismiss -> onDismiss()
        }
    }

    PebblesScreen(
        modifier = modifier.background(colors.surface),
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
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.action_save),
                            onClick = viewModel::save,
                            enabled = uiState.canSave,
                            color = if (uiState.canSave) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
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
                    .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.settings_name_label)) },
                placeholder = { Text(stringResource(R.string.create_soul_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            PebblesListSection(
                header = stringResource(R.string.create_glyph_header),
                rowPadding = PebblesListDefaults.ListItemRowPadding,
                rows =
                    listOf(
                        {
                            ListItem(
                                leadingContent = {
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
                                },
                                headlineContent = { Text(stringResource(R.string.soul_form_glyph_choose)) },
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(PebblesIconToken.MEDIUM.size),
                                    )
                                },
                                modifier = Modifier.clickable(onClick = viewModel::openPicker),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        },
                    ),
            )

            if (uiState.didSaveFail) {
                Text(
                    text = stringResource(uiState.saveErrorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
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
