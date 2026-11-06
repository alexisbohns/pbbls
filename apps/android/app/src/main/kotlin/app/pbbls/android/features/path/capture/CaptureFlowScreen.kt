package app.pbbls.android.features.path.capture

import android.content.ActivityNotFoundException
import android.util.Log
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.features.path.flow.RecordFlowChrome
import app.pbbls.android.features.path.flow.RecordStepAction
import app.pbbls.android.features.path.flow.RecordStepScaffold
import app.pbbls.android.features.path.flow.steps.RecordMomentStep
import app.pbbls.android.features.path.flow.steps.RecordPhotoControls
import app.pbbls.android.features.path.flow.steps.RecordPhotoStep

private const val TAG = "capture-flow"

/** As the record flow: enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * Capture (#1048, #1515): the photo, then the moment page, then `save_moment`. A full-screen modal
 * entry (A-14). [onSaved] returns to Path, [onSavedThenComplete] hands the new
 * moment id over to Complete, and [onDismiss] is every other exit: ✕ or back
 * on the first reachable step, silently when nothing was entered, else through the
 * close dialog (R2). [chainsComplete] (Home's New pebble, #1383 D7) leaves the page one save,
 * "Save & make it a pebble", always followed by Complete. [startsAtMomentPage] is the quick moment
 * (D13); [promptReason] is the onboarding hand-off's slug (#1510 D9).
 */
@Composable
fun CaptureFlowScreen(
    chainsComplete: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onSavedThenComplete: (momentId: String) -> Unit,
    modifier: Modifier = Modifier,
    startsAtMomentPage: Boolean = false,
    promptReason: String? = null,
    viewModel: CaptureFlowViewModel = hiltViewModel(),
) {
    // Before the state is first read, so a quick moment never flashes the photo step. Idempotent. It
    // returns a flag only because `remember` may not return Unit; nothing reads it.
    remember(viewModel, chainsComplete, startsAtMomentPage, promptReason) {
        viewModel.start(chainsComplete, startsAtMomentPage, promptReason)
        true
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Each buzz asks Pebbles' haptics switch (#1242 D23).
    val haptic = rememberTapHaptics { viewModel.hapticsOn }

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

    // The camera is another process: its answer may reach a new ViewModel, which finds the shot in saved state.
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            viewModel.onCameraResult(taken)
        }
    // Results land before the resume, so a shot still out here never got its answer.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    CaptureFlowContent(
        uiState = uiState,
        model = viewModel.machine(),
        onPickPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            viewModel.onCameraRequested()?.let { uri ->
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "no camera app answered", e)
                    viewModel.onCameraLaunchFailed()
                }
            }
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
    onTakePhoto: () -> Unit,
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
            justSave = stringResource(R.string.capture_just_save),
            saveAndPebble = stringResource(R.string.capture_save_and_pebble),
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
            canGoBack = flow.step.previous != null && !flow.isFrozen && !uiState.startsAtMomentPage,
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
                contentScrolls = step != CaptureStep.PHOTO,
                fixedSlot =
                    if (step == CaptureStep.PHOTO) {
                        {
                            RecordPhotoControls(
                                enabled = !flow.isFrozen,
                                showsCamera = uiState.cameraAvailable,
                                onJustWrite = model::advance,
                                onCamera = onTakePhoto,
                            )
                        }
                    } else {
                        null
                    },
                action =
                    actionFor(
                        flow.copy(step = step),
                        uiState.canSaveNow,
                        uiState.chainsComplete,
                        labels,
                        onSave,
                        onSaveThenComplete,
                    ),
            ) {
                when (step) {
                    CaptureStep.PHOTO ->
                        RecordPhotoStep(
                            snap = uiState.snap,
                            isPicking = uiState.snap == null && uiState.snapBlockedMessageRes != null,
                            enabled = !flow.isFrozen,
                            onPick = onPickPhoto,
                        )
                    CaptureStep.MOMENT ->
                        RecordMomentStep(
                            draft = flow.draft,
                            whenSource = flow.whenSource,
                            snap = uiState.snap,
                            errorRes = flow.errorRes,
                            onNameChange = { model.setName(it) },
                            onWordsChange = { model.setDescription(it) },
                            onWhenChange = { model.setHappenedAt(it) },
                            // Change photo and Add a photo go back to the photo step (iOS).
                            onChangePhoto = model::back,
                            onRetry = onRetryPhoto,
                            onRemove = onRemovePhoto,
                        )
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
 * The step's actions: the photo step has none (its three choices are in the screen, and a pick advances
 * on its own). The moment page has "Save & make it a pebble" for its
 * primary and "Just save" beneath it, which Home's New pebble ([chainsComplete], #1383 D7) does not offer.
 */
private fun actionFor(
    state: CaptureFlowState,
    canSaveNow: Boolean,
    chainsComplete: Boolean,
    labels: CaptureLabels,
    onSave: () -> Unit,
    onSaveThenComplete: () -> Unit,
): RecordStepAction? =
    when (state.step) {
        CaptureStep.PHOTO -> null
        CaptureStep.MOMENT -> {
            val primary =
                RecordStepAction.Primary(
                    label = labels.saveAndPebble,
                    enabled = canSaveNow,
                    isLoading = state.isSaving && (chainsComplete || state.savingThenComplete),
                    onClick = onSaveThenComplete,
                )
            if (chainsComplete) {
                primary
            } else {
                RecordStepAction.PrimaryWithSecondary(
                    primary = primary,
                    secondaryLabel = labels.justSave,
                    onSecondary = onSave,
                    secondaryIsLoading = state.isSaving && !state.savingThenComplete,
                )
            }
        }
    }

/** Resolved strings, so the action builder stays a pure function. */
private data class CaptureLabels(
    val justSave: String,
    val saveAndPebble: String,
)
