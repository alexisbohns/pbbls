package app.pbbls.android.features.path

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.features.path.create.PebbleForm

/**
 * Edit moment (R5): when, name, description and photo, saved with
 * `save_moment` and nothing else. Removing the saved photo only detaches it;
 * Save sends the whole snap set and the server enforces "a name or a photo"
 * in the same write (R6), so Save stays disabled, with the hint shown, while
 * the trimmed name is empty and no photo is attached.
 *
 * Keeps a `NavigationBackHandler` like Edit pebble: `viewModel.dismiss()`
 * runs the pending-upload cleanup a bare `Navigator.goBack()` would skip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditMomentScreen(
    momentId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditMomentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(momentId) { viewModel.start(momentId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            EditMomentEffect.Saved -> onSaved()
            EditMomentEffect.Dismissed -> onDismiss()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPhotoPicked)
        }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { viewModel.dismiss() }

    EditMomentContent(
        uiState = uiState,
        onDraftChange = viewModel::onDraftChange,
        onCancel = viewModel::dismiss,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        onAddPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRetryPhoto = viewModel::onRetryPhoto,
        onRemovePhoto = viewModel::onRemovePhoto,
        onRemoveExistingSnap = viewModel::onRemoveExistingSnap,
        hapticsOn = { viewModel.hapticsOn },
        modifier = modifier,
    )
}

/**
 * Edit moment without its ViewModel: the bar and the three states. [EditMomentScreen] wires it; screenshots drive it.
 * [hapticsOn] is Pebbles' haptics switch (#1242 D23), handed to the form.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditMomentContent(
    uiState: EditMomentUiState,
    onDraftChange: (PebbleDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    hapticsOn: () -> Boolean,
    modifier: Modifier = Modifier,
    onAddPhoto: () -> Unit = {},
    onRetryPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onRemoveExistingSnap: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val content = uiState as? EditMomentUiState.Content

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .readableWidth(),
    ) {
        EditTopBar(
            title = stringResource(R.string.edit_moment),
            isSaving = content?.isSaving == true,
            saveEnabled = content?.canSave == true,
            onCancel = onCancel,
            onSave = onSave,
        )
        // Exhaustive with no `else`: a new EditMomentUiState case must render.
        when (val state = uiState) {
            EditMomentUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }
            EditMomentUiState.Error ->
                EditLoadError(onRetry = onRetry)
            is EditMomentUiState.Content ->
                Column(Modifier.fillMaxSize()) {
                    PebbleForm(
                        draft = state.draft,
                        onDraftChange = onDraftChange,
                        // The pebble fields are hidden here (R5), so their lists stay empty.
                        domains = emptyList(),
                        souls = emptyList(),
                        collections = emptyList(),
                        selectedEmotion = null,
                        saveError = state.saveErrorRes?.let { stringResource(it) },
                        showMomentFields = true,
                        showPebbleFields = false,
                        modifier = Modifier.weight(1f),
                        formSnap = state.snap,
                        onAddPhoto = onAddPhoto,
                        onRetryPending = onRetryPhoto,
                        onRemovePending = onRemovePhoto,
                        onRemoveExistingSnap = onRemoveExistingSnap,
                        hapticsOn = hapticsOn,
                    )
                    if (!state.hasContent) {
                        Text(
                            stringResource(R.string.edit_moment_needs_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
        }
    }
}
