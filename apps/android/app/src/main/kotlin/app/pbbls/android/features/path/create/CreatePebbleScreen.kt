package app.pbbls.android.features.path.create

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.ui.ObserveUiEffects

/**
 * The create-pebble surface (D5) — ports iOS `CreatePebbleSheet`. Owns the
 * [PebbleDraft] (D4) plus the picked glyph, save-in-flight, and inline
 * save-error state, and reads the write / reference / karma / palette services
 * here so the hosted [PebbleForm] stays pure and previewable. `save()` branches
 * on [ComposeResult]: `Success` flashes karma (`karma_delta`, `PEBBLE_CREATED`)
 * and reveals; `SoftSuccess` skips the flash but still reveals; `Failure`
 * surfaces an inline error (D16). Self-applies `safeDrawingPadding()` +
 * `imePadding()`, so the caller composes it in an edge-to-edge (unpadded) slot,
 * sibling to the detail cover in `PathScreen`'s OUTER Box.
 *
 * M47 adds draft mode: a "Save as draft" action ungated by the publish
 * requirements (design D5), a debounced local snapshot of the open form as crash
 * insurance, and [resumeDraftId] to hydrate from a server draft.
 *
 * [resumeDraftId] carries an id rather than the whole draft record (#852): a
 * navigation key can only carry an id, and [CreatePebbleViewModel] fetches the
 * row itself once reference data has loaded.
 *
 * A pushed entry now (#852). Keeps a `BackHandler`, unlike its detail/drafts
 * siblings — deliberately: `viewModel.cancel()` runs snap cleanup
 * (`cancelAndCleanup`) and releases the composer's start guard, both of which a
 * bare `Navigator.goBack()` would skip, so system back has to route through it
 * rather than through `NavDisplay`'s default. The handler is now
 * unconditionally **enabled** rather than `enabled = !uiState.isBusy` (D9): a
 * *disabled* `BackHandler` declines the event instead of blocking it, so it
 * fell through and popped this screen anyway while busy, skipping the cleanup
 * the disabled state was trying to preserve. `cancel()` already no-ops while
 * `isBusy`, so always calling it gets both cases right.
 */
@Composable
fun CreatePebbleScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    resumeDraftId: String? = null,
    onDraftSaved: () -> Unit = onCancel,
    viewModel: CreatePebbleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refs = LocalReferenceDataService.current
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    // Re-run when reference data lands: the coordinator refuses to hydrate
    // before it, so the first call may legitimately decide nothing (#647).
    LaunchedEffect(resumeDraftId, refs.hasLoaded) { viewModel.start(resumeDraftId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is CreatePebbleEffect.Created -> onCreated(effect.pebbleId)
            CreatePebbleEffect.DraftSaved -> onDraftSaved()
            CreatePebbleEffect.Cancelled -> onCancel()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPhotoPicked)
        }

    BackHandler { viewModel.cancel() }

    val draft = uiState.draft
    val selectedEmotion = draft.emotionId?.let { palettes.byEmotionId[it] }
    val saveError = uiState.saveErrorRes?.let { stringResource(it) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding()
                .imePadding(),
    ) {
        CreateTopBar(
            saveEnabled = draft.isValid,
            isSaving = uiState.isBusy,
            onCancel = viewModel::cancel,
            onSave = viewModel::save,
        )
        PebbleForm(
            draft = draft,
            onDraftChange = viewModel::onDraftChange,
            domains = refs.domains,
            souls = refs.souls,
            collections = refs.collections,
            selectedEmotion = selectedEmotion,
            selectedGlyph = uiState.selectedGlyph,
            onGlyphPicked = viewModel::onGlyphPicked,
            saveError = saveError,
            modifier = Modifier.weight(1f),
            formSnap = uiState.snap,
            onAddPhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRetryPending = viewModel::onRetryPhoto,
            onRemovePending = viewModel::onRemovePhoto,
        )
        // Quick capture: ungated, unlike Save (design D5). "Just a name" is a
        // valid draft. Mirrors the iOS .bottomBar toolbar item.
        val draftEnabled = viewModel.isSavableAsDraft() && !uiState.isBusy
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisibilityChip(value = draft.visibility, onChange = { viewModel.onDraftChange(draft.copy(visibility = it)) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::saveAsDraft, enabled = draftEnabled) {
                PebblesText(
                    text = stringResource(R.string.draft_save),
                    style = PebblesTypography.body,
                    color = if (draftEnabled) accent.primary else system.muted,
                )
            }
        }
    }

    // Crash-insurance restore. Only offered when not already resuming a server
    // draft, and only once per composition.
    if (uiState.isRestorePromptPresented) {
        AlertDialog(
            onDismissRequest = viewModel::discardRestore,
            title = {
                PebblesText(
                    text = stringResource(R.string.draft_restore_title),
                    style = PebblesTypography.headlineEmphasized,
                    color = system.foreground,
                )
            },
            text = {
                PebblesText(
                    text = stringResource(R.string.draft_restore_body),
                    style = PebblesTypography.body,
                    color = system.secondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptRestore) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_confirm),
                        style = PebblesTypography.body,
                        color = accent.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardRestore) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_discard),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                }
            },
        )
    }
}

/**
 * Create-surface top bar: Cancel (left), the title (centered), and a Save
 * button that swaps to an inline spinner while [isSaving] and is disabled until
 * the draft is valid. Ports `CreatePebbleSheet`'s toolbar, composed on the
 * shared [PebblesTopBar]; keeps the shipped M39 look (headline title, accent
 * buttons) via the style overrides — see the PebblesTopBar doc for the
 * iOS-idiom defaults new screens should use.
 */
@Composable
private fun CreateTopBar(
    saveEnabled: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    PebblesTopBar(
        title = stringResource(R.string.create_new_pebble),
        titleStyle = PebblesTypography.headlineEmphasized,
        titleColor = system.foreground,
        leading = {
            PebblesTopBarTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                color = accent.primary,
            )
        },
        trailing = {
            if (isSaving) {
                CircularProgressIndicator(
                    color = accent.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSave,
                    enabled = saveEnabled,
                    color = if (saveEnabled) accent.primary else system.muted,
                )
            }
        },
    )
}
