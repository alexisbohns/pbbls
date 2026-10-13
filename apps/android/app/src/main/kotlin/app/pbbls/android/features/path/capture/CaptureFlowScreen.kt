package app.pbbls.android.features.path.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.features.path.record.RecordFlowChrome
import app.pbbls.android.features.path.record.RecordStepAction
import app.pbbls.android.features.path.record.RecordStepScaffold
import app.pbbls.android.features.path.record.steps.RecordNameStep
import app.pbbls.android.features.path.record.steps.RecordPhotoStep
import app.pbbls.android.features.path.record.steps.RecordWhenStep

/** As the record flow: enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * Capture (#1048): photo, when, name, then `save_moment`. A full-screen modal
 * entry (A-14). [onSaved] returns to Path, [onSavedThenComplete] hands the new
 * moment id over to Complete, and [onDismiss] is every other exit: ✕ or back
 * on the photo step, silently when nothing was entered, else through the
 * close dialog (R2).
 */
@Composable
fun CaptureFlowScreen(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onSavedThenComplete: (momentId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureFlowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberTapHaptics()

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is CaptureFlowEffect.Haptic -> haptic(effect.flavor)
            CaptureFlowEffect.Saved -> onSaved()
            is CaptureFlowEffect.SavedThenComplete -> onSavedThenComplete(effect.momentId)
            CaptureFlowEffect.Dismiss -> onDismiss()
        }
    }

    // Always enabled: back walks the steps and asks to close on the first one;
    // it never falls through to whatever hosts the flow.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { viewModel.onSystemBack() }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPhotoPicked)
        }

    CaptureFlowContent(
        uiState = uiState,
        model = viewModel.machine(),
        onPickPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRetryPhoto = viewModel::onRetryPhoto,
        onRemovePhoto = viewModel::onRemovePhoto,
        onSave = { viewModel.save(thenComplete = false) },
        onSaveThenComplete = { viewModel.save(thenComplete = true) },
        onClose = viewModel::onCloseRequested,
        onSaveFromClose = viewModel::onSaveFromClose,
        onKeepGoing = viewModel::onKeepGoing,
        onDiscard = viewModel::onDiscard,
        modifier = modifier,
    )
}

/** Stateless, so the previews render every step and the close dialog without a ViewModel. */
@Composable
fun CaptureFlowContent(
    uiState: CaptureFlowUiState,
    model: CaptureFlowModel,
    onPickPhoto: () -> Unit,
    onRetryPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    onSaveThenComplete: () -> Unit,
    onClose: () -> Unit,
    onSaveFromClose: () -> Unit,
    onKeepGoing: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = uiState.flow
    val labels =
        CaptureLabels(
            skip = stringResource(R.string.record_action_skip),
            done = stringResource(R.string.action_done),
            continueLabel = stringResource(R.string.record_action_continue),
            save = stringResource(R.string.capture_save),
            makePebbleNow = stringResource(R.string.capture_make_pebble_now),
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .imePadding()
                .readableWidth(),
    ) {
        RecordFlowChrome(
            position = flow.step.ordinal + 1,
            total = CaptureStep.entries.size,
            canGoBack = flow.step.previous != null,
            onBack = model::back,
            onClose = onClose,
        )

        val reduceMotion = rememberReduceMotion()
        AnimatedContent(
            targetState = flow.step,
            transitionSpec = {
                if (reduceMotion) return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None
                val entering = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                val spec = tween<Float>(STEP_TRANSITION_MS)
                val slideSpec = tween<IntOffset>(STEP_TRANSITION_MS)
                val enter = slideInHorizontally(slideSpec) { it * entering } + fadeIn(spec)
                val exit = slideOutHorizontally(slideSpec) { it * -entering } + fadeOut(spec)
                enter togetherWith exit using SizeTransform(clip = false)
            },
            label = "captureFlowStep",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { step ->
            RecordStepScaffold(
                title = stringResource(step.titleRes),
                subtitle = step.subtitleRes?.let { stringResource(it) },
                action = actionFor(flow.copy(step = step), uiState.canSaveNow, model, labels, onSave, onSaveThenComplete),
            ) {
                when (step) {
                    CaptureStep.PHOTO ->
                        RecordPhotoStep(snap = uiState.snap, onPick = onPickPhoto, onRetry = onRetryPhoto, onRemove = onRemovePhoto)
                    CaptureStep.WHEN ->
                        RecordWhenStep(
                            happenedAt = flow.draft.happenedAt,
                            onChange = { model.setHappenedAt(it) },
                            seededFromPhoto = uiState.seededFromPhoto,
                        )
                    CaptureStep.NAME ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RecordNameStep(name = flow.draft.name, limit = CaptureFlowModel.NAME_LIMIT, onChange = { model.setName(it) })
                            flow.errorRes?.let {
                                Text(
                                    text = stringResource(it),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                }
            }
        }
    }

    if (uiState.isCloseConfirmPresented) {
        CaptureCloseDialog(
            offersSave = uiState.closeOffersSave,
            onSave = onSaveFromClose,
            onDiscard = onDiscard,
            onKeepGoing = onKeepGoing,
        )
    }
}

/**
 * "Keep this moment?" (R2). Save moment only when the moment is savable now;
 * Discard deletes the uploaded photo; Keep going stays. Material lays out two
 * action slots and the dialog needs three, so the two exits share the confirm
 * slot, stacked, and the stay-put option keeps the dismiss slot.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptureCloseDialog(
    offersSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        // Tapping outside is "keep going": the least destructive of the three.
        onDismissRequest = onKeepGoing,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.capture_close_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (offersSave) {
                    TextButton(onClick = onSave) {
                        Text(
                            text = stringResource(R.string.capture_save),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.primary,
                        )
                    }
                }
                TextButton(onClick = onDiscard) {
                    Text(
                        text = stringResource(R.string.record_close_discard),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) {
                Text(
                    text = stringResource(R.string.record_close_keep_going),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * The step's actions (A-1): the photo is optional (Skip, then Done once
 * attached; a pick never advances), when always has an answer, and the name
 * step carries R1's two saves.
 */
private fun actionFor(
    state: CaptureFlowState,
    canSaveNow: Boolean,
    model: CaptureFlowModel,
    labels: CaptureLabels,
    onSave: () -> Unit,
    onSaveThenComplete: () -> Unit,
): RecordStepAction =
    when (state.step) {
        CaptureStep.PHOTO ->
            RecordStepAction.Text(
                label = if (state.photoButtonIsSkip) labels.skip else labels.done,
                onClick = model::advance,
            )
        CaptureStep.WHEN ->
            RecordStepAction.Primary(
                label = labels.continueLabel,
                enabled = true,
                isLoading = false,
                onClick = model::advance,
            )
        CaptureStep.NAME ->
            RecordStepAction.PrimaryWithSecondary(
                primary =
                    RecordStepAction.Primary(
                        label = labels.save,
                        enabled = canSaveNow,
                        isLoading = state.isSaving,
                        onClick = onSave,
                    ),
                secondaryLabel = labels.makePebbleNow,
                onSecondary = onSaveThenComplete,
            )
    }

/** Resolved strings, so the action builder stays a pure function. */
private data class CaptureLabels(
    val skip: String,
    val done: String,
    val continueLabel: String,
    val save: String,
    val makePebbleNow: String,
)
