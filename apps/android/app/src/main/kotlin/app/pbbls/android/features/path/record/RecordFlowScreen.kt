package app.pbbls.android.features.path.record

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.features.path.create.pickers.rememberGlyphPickerState
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.record.steps.RecordSuccessStep
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.rememberTapHaptics
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.ui.ObserveUiEffects

/** Enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * The step-by-step pebble composer (M58) — ports iOS `RecordFlowView`, and the
 * default way to record a pebble on Android. `CreatePebbleScreen` stays in the
 * tree and is reachable by long-pressing the same "New pebble" entry (D1).
 *
 * Since #849 this layer is the *view*: the coordinators, the writes and the
 * draft lifecycle live in [RecordFlowViewModel], and what is left here is the
 * three things that genuinely need a composition — the photo picker (an
 * Activity result contract), the haptics (they need a `View`), and the layout.
 *
 * Self-applies `safeDrawingPadding()` + `imePadding()`, so the caller composes
 * it in an edge-to-edge (unpadded) slot, sibling to the detail cover in
 * `PathScreen`'s outer Box.
 *
 * [onPublished] fires as soon as the pebble publishes, while the success step is
 * still up, so the Path is already reloaded by the time the user exits (D10).
 * [onDismiss] is the exit: cancel, and the success step's own button.
 *
 * [resumeDraftId] carries an id rather than the whole draft record (#852): a
 * navigation key can only carry an id, and [RecordFlowViewModel] fetches the row
 * itself once reference data has loaded.
 */
@Composable
fun RecordFlowScreen(
    onPublished: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    resumeDraftId: String? = null,
    onDraftSaved: () -> Unit = onDismiss,
    viewModel: RecordFlowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val model = viewModel.machine()
    val refs = LocalReferenceDataService.current
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    val haptic = rememberTapHaptics()
    val glyphPickerState = rememberGlyphPickerState()

    // The ViewModel is activity-scoped (the cover is a conditionally-composed
    // child, not a destination — #852), so opening is explicit. `startFlow`
    // guards itself, which is what keeps a rotation from re-hydrating over what
    // the user has typed since.
    LaunchedEffect(resumeDraftId, refs.hasLoaded) { viewModel.startFlow(resumeDraftId) }

    // Haptics come back out as effects because a ViewModel has no View to buzz;
    // every interaction still routes through RecordFlowModel (M58 D4).
    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is RecordFlowEffect.Haptic -> haptic(effect.flavor)
            is RecordFlowEffect.Published -> onPublished(effect.pebbleId)
            RecordFlowEffect.Dismiss -> onDismiss()
            RecordFlowEffect.DraftSaved -> onDraftSaved()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(viewModel::onPhotoPicked)
        }

    val flow = uiState.flow
    val snapBlockedMessage = uiState.snapBlockedMessageRes?.let { stringResource(it) }
    val publishError = flow.publishErrorRes?.let { stringResource(it) }

    // System back mirrors the chrome: unwind an open glyph swap first (that
    // state belongs to the picker, not the flow), then let the flow decide.
    //
    // Always enabled, never conditional: a disabled handler lets back fall
    // through to whatever hosts the cover, so "back does nothing here" has to be
    // an explicit branch rather than an absent handler. That is also why the
    // terminal step handles it — it has no back chevron, but the system button
    // exists regardless and has to mean "leave", not "exit the app".
    BackHandler { viewModel.onSystemBack(unwindGlyphPicker = glyphPickerState::unwind) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding()
                .imePadding(),
    ) {
        if (flow.step != RecordStep.SUCCESS) {
            RecordFlowChrome(
                step = flow.step,
                onBack = { model.back() },
                onClose = viewModel::onCloseRequested,
            )
        }

        AnimatedContent(
            targetState = flow.step,
            transitionSpec = {
                // Direction comes from the transition itself rather than a
                // remembered "previous step", which would be a second source of
                // truth for something the animation already knows.
                val entering = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                val spec = tween<Float>(STEP_TRANSITION_MS)
                val slideSpec = tween<IntOffset>(STEP_TRANSITION_MS)
                val enter = slideInHorizontally(slideSpec) { it * entering } + fadeIn(spec)
                val exit = slideOutHorizontally(slideSpec) { it * -entering } + fadeOut(spec)
                enter togetherWith exit using SizeTransform(clip = false)
            },
            label = "recordFlowStep",
            // weight, not fillMaxSize: the chrome above is measured first, and a
            // weighted child is the unambiguous way to say "everything left".
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { step ->
            val response = flow.published
            if (step == RecordStep.SUCCESS && response != null) {
                RecordSuccessStep(
                    name = flow.draft.name,
                    renderSvg = response.renderSvg,
                    karmaDelta = response.karmaDelta,
                    valence = flow.draft.valence ?: Valence.NEUTRAL_MEDIUM,
                    palette = flow.draft.emotionId?.let { palettes.palette(it) },
                    onExit = viewModel::onExit,
                )
            } else {
                RecordStepScaffold(
                    title = stringResource(step.titleRes),
                    subtitle = step.subtitleRes?.let { stringResource(it) },
                    action = actionFor(step, flow, model, snapBlockedMessage, onPublish = viewModel::publish),
                    contentScrolls = !step.bringsOwnScroll,
                ) {
                    RecordStepContent(
                        step = step,
                        draft = flow.draft,
                        model = model,
                        domains = refs.domains,
                        collections = refs.collections,
                        snap = uiState.snap,
                        seededFromPhoto = uiState.seededFromPhoto,
                        snapBlockedMessage = snapBlockedMessage,
                        publishError = publishError,
                        glyphPickerState = glyphPickerState,
                        onPickPhoto = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRetryPhoto = viewModel::onRetryPhoto,
                        onRemovePhoto = viewModel::onRemovePhoto,
                        onGlyphPicked = { model.selectGlyph(it.id) },
                    )
                }
            }
        }
    }

    // Crash-insurance restore, unchanged from the sheet (M47): local autosave is
    // invisible, and the prompt fires on entry.
    if (uiState.isRestorePromptPresented) {
        AlertDialog(
            onDismissRequest = viewModel::discardRestore,
            containerColor = system.background,
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

    if (uiState.isCloseConfirmPresented) {
        CloseConfirmDialog(
            onSaveAsDraft = viewModel::onSaveAsDraft,
            onDiscard = viewModel::onDiscard,
            onKeepGoing = viewModel::onKeepGoing,
        )
    }
}

/**
 * The ✕ confirmation (D9). The moment a user wants to keep a half-finished
 * pebble is precisely the moment they try to leave, so the choice lives here
 * rather than taking permanent residence in the chrome — which converts an
 * accidental discard into a deliberate one.
 *
 * `internal` so the screenshot preview can render it directly.
 */
@Composable
internal fun CloseConfirmDialog(
    onSaveAsDraft: () -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        // Tapping outside is "keep going": the least destructive of the three.
        onDismissRequest = onKeepGoing,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.record_close_title),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        // Material lays out exactly two action slots, and the flow needs three —
        // so the two exits share the confirm slot and the stay-put option keeps
        // the dismiss slot, where a cancel is expected.
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDiscard) {
                    PebblesText(
                        text = stringResource(R.string.record_close_discard),
                        style = PebblesTypography.body,
                        color = PebblesDestructive,
                    )
                }
                TextButton(onClick = onSaveAsDraft) {
                    PebblesText(
                        text = stringResource(R.string.draft_save),
                        style = PebblesTypography.body,
                        color = accent.primary,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) {
                PebblesText(
                    text = stringResource(R.string.record_close_keep_going),
                    style = PebblesTypography.body,
                    color = system.secondary,
                )
            }
        },
    )
}

/**
 * The one action a step offers, if any. Tile steps offer none — the pick is the
 * advance (M58 D3); optional steps carry a text button reading Skip while empty
 * and Done once filled.
 */
@Composable
private fun actionFor(
    step: RecordStep,
    flow: RecordFlowState,
    model: RecordFlowModel,
    snapBlockedMessage: String?,
    onPublish: () -> Unit,
): RecordStepAction? =
    when (step) {
        RecordStep.EMOTION, RecordStep.DOMAIN, RecordStep.SUCCESS -> null

        // Unlike the other tile steps, valence commits without advancing (the
        // fan is worth looking at once a stone is lit), so it needs a button.
        RecordStep.VALENCE, RecordStep.WHEN, RecordStep.NAME ->
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_continue),
                enabled = flow.isAnswered,
                isLoading = false,
                onClick = { model.advance() },
            )

        RecordStep.PRIVACY ->
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_publish),
                enabled = snapBlockedMessage == null && flow.draft.isValid,
                isLoading = flow.isPublishing,
                onClick = onPublish,
            )

        RecordStep.PHOTO, RecordStep.SOULS, RecordStep.COLLECTION, RecordStep.GLYPH ->
            RecordStepAction.Text(
                label =
                    stringResource(
                        if (flow.optionalButtonIsSkip) R.string.record_action_skip else R.string.action_done,
                    ),
                onClick = { model.advance() },
            )
    }
