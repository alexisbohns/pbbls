package app.pbbls.android.features.path

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.features.path.create.VisibilityChip
import app.pbbls.android.features.path.models.renderHeightDp
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.ui.ObserveUiEffects

/**
 * Full-screen surface for editing an existing pebble — ports EditPebbleSheet.swift
 * (minus the photo flow, a milestone non-goal). Loads the PebbleDetail via B's
 * fetch, prefills the shared PebbleForm via PebbleDraft.from(detail), renders the
 * current render_svg at the top at the valence-derived height with the palette
 * stroke color, and saves through PebbleWriteService.update (D2/D3). Soft-success
 * (any 5xx) advances; the pebbleEnriched flash fires only when karma_delta > 0
 * (D10, guarded inside KarmaNotificationService).
 *
 * A pushed entry now (#852). Keeps a `BackHandler`, unlike its detail/drafts
 * siblings — deliberately: `viewModel.dismiss()` runs snap cleanup
 * (`cancelAndCleanup`) that a bare `Navigator.goBack()` would skip, so system
 * back has to route through it rather than through `NavDisplay`'s default. The
 * handler is now unconditionally **enabled** rather than
 * `enabled = content?.isSaving != true` (D9): a *disabled* `BackHandler`
 * declines the event instead of blocking it, so it fell through and popped
 * this screen anyway while saving, skipping the cleanup the disabled state was
 * trying to preserve. `dismiss()` already no-ops while `isSaving`, so always
 * calling it gets both cases right.
 */
@Composable
fun EditPebbleScreen(
    pebbleId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditPebbleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val referenceData = LocalReferenceDataService.current
    val palettes = LocalEmotionPaletteService.current
    val isDark = isSystemInDarkTheme()
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    LaunchedEffect(pebbleId) { viewModel.start(pebbleId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            EditPebbleEffect.Saved -> onSaved()
            EditPebbleEffect.Dismissed -> onDismiss()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPhotoPicked)
        }

    val content = uiState as? EditPebbleUiState.Content
    BackHandler { viewModel.dismiss() }

    Column(
        modifier
            .fillMaxSize()
            .background(system.background)
            .safeDrawingPadding(),
    ) {
        EditTopBar(
            isSaving = content?.isSaving == true,
            saveEnabled = content?.draft?.isValid == true,
            onCancel = viewModel::dismiss,
            onSave = viewModel::save,
        )
        // Exhaustive with no `else`: a new EditPebbleUiState case must render.
        when (val state = uiState) {
            EditPebbleUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = accent.primary)
                }
            EditPebbleUiState.Error ->
                EditLoadError(onRetry = viewModel::retry)
            is EditPebbleUiState.Content -> {
                // The stroke colour depends on the active colour scheme, so it
                // is resolved here rather than in the load (iOS parity:
                // EditPebbleSheet uses strokeHex(colorScheme), NOT the
                // intensity-based pebbleFrameColors the read banner uses).
                val strokeColor =
                    state.emotionId?.let { palettes.palette(it)?.strokeHex(isDark) } ?: accent.primaryHex
                val selectedEmotion = state.draft.emotionId?.let { palettes.byEmotionId[it] }
                val saveError = state.saveErrorRes?.let { stringResource(it) }
                Column(Modifier.fillMaxSize()) {
                    PebbleForm(
                        draft = state.draft,
                        onDraftChange = viewModel::onDraftChange,
                        domains = referenceData.domains,
                        souls = referenceData.souls,
                        collections = referenceData.collections,
                        selectedEmotion = selectedEmotion,
                        selectedGlyph = state.selectedGlyph,
                        onGlyphPicked = viewModel::onGlyphPicked,
                        saveError = saveError,
                        renderSvg = state.renderSvg,
                        strokeColor = strokeColor,
                        renderHeight = state.renderHeightDp.dp,
                        modifier = Modifier.weight(1f),
                        formSnap = state.snap,
                        onAddPhoto = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRetryPending = viewModel::onRetryPhoto,
                        onRemovePending = viewModel::onRemovePhoto,
                        isRemovingExistingSnap = state.isRemovingExistingSnap,
                        onRemoveExistingSnap = viewModel::onRemoveExistingSnap,
                    )
                    // Grade chip (M51) — mirrors iOS EditPebbleSheet's bottomBar
                    // ToolbarItemGroup, matching CreatePebbleScreen's row treatment.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VisibilityChip(
                            value = state.draft.visibility,
                            onChange = { viewModel.onDraftChange(state.draft.copy(visibility = it)) },
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTopBar(
    isSaving: Boolean,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            PebblesText(
                stringResource(R.string.action_cancel),
                PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        PebblesText(
            stringResource(R.string.edit_pebble_title),
            PebblesTypography.buttonLabel,
            color = system.foreground,
        )
        Spacer(Modifier.weight(1f))
        if (isSaving) {
            Box(Modifier.size(48.dp), Alignment.Center) {
                CircularProgressIndicator(
                    color = accent.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            TextButton(onClick = onSave, enabled = saveEnabled) {
                PebblesText(
                    stringResource(R.string.action_save),
                    PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        }
    }
}

@Composable
private fun EditLoadError(onRetry: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PebblesText(
            stringResource(R.string.pebble_detail_load_error),
            PebblesTypography.body,
            color = system.secondary,
        )
        TextButton(onClick = onRetry) {
            PebblesText(
                stringResource(R.string.pebble_detail_retry),
                PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}
