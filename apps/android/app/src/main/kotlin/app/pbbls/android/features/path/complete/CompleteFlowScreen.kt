package app.pbbls.android.features.path.complete

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.Valence
import app.pbbls.android.features.path.flow.RecordFlowChrome
import app.pbbls.android.features.path.flow.RecordStepAction
import app.pbbls.android.features.path.flow.RecordStepScaffold
import app.pbbls.android.features.path.flow.steps.RecordSuccessStep

/** As the record flow: enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * The Complete flow (#1048): a moment becomes a pebble in six steps, then a
 * success screen. A full-screen modal entry (A-14). ✕ never asks (A-4); the
 * answers stay in the per-moment autosave. [onDismiss] is every exit: ✕, back
 * on the first step, and the success screen's button.
 */
@Composable
fun CompleteFlowScreen(
    momentId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompleteFlowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refs = LocalReferenceDataService.current
    val palettes = LocalEmotionPaletteService.current
    // Each buzz asks Pebbles' haptics switch (#1242 D23).
    val haptic = rememberTapHaptics { viewModel.hapticsOn }

    LaunchedEffect(momentId) { viewModel.start(momentId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is CompleteFlowEffect.Haptic -> haptic(effect.flavor)
            CompleteFlowEffect.Dismiss -> onDismiss()
        }
    }

    // Always enabled, as in the record flow: back walks the steps, closes on
    // the first one, and leaves from success; it never falls through.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { viewModel.onSystemBack() }

    CompleteFlowContent(
        uiState = uiState,
        model = viewModel.machine(),
        domains = refs.domains,
        collections = refs.collections,
        palette =
            uiState.flow.answers.emotionId
                ?.let(palettes::palette),
        onClose = viewModel::onClose,
        onComplete = viewModel::complete,
        onExit = viewModel::onExit,
        onAchievementCheck = viewModel::onAchievementCheck,
        modifier = modifier,
    )
}

/** Stateless, so the previews render every step without a ViewModel. */
@Composable
fun CompleteFlowContent(
    uiState: CompleteFlowUiState,
    model: CompleteFlowModel,
    domains: List<Domain>,
    collections: List<PebbleCollection>,
    palette: EmotionPalette?,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
) {
    val flow = uiState.flow
    val labels =
        CompleteLabels(
            continueLabel = stringResource(R.string.record_action_continue),
            skip = stringResource(R.string.record_action_skip),
            done = stringResource(R.string.action_done),
            makePebble = stringResource(R.string.complete_cta),
        )
    val error = flow.errorRes?.let { stringResource(it) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .readableWidth(),
    ) {
        if (flow.step != CompleteStep.SUCCESS) {
            RecordFlowChrome(
                position = (flow.step.dotIndex ?: 0) + 1,
                total = CompleteStep.counted.size,
                canGoBack = flow.step.previous != null,
                onBack = model::back,
                onClose = onClose,
            )
        }

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
            label = "completeFlowStep",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { step ->
            val completed = flow.completed
            if (step == CompleteStep.SUCCESS && completed != null) {
                RecordSuccessStep(
                    name = uiState.momentName,
                    glyph = completed.glyph,
                    karmaDelta = completed.karmaDelta,
                    valence = flow.answers.valence ?: Valence.NEUTRAL_MEDIUM,
                    palette = palette,
                    onExit = onExit,
                )
            } else {
                RecordStepScaffold(
                    title = stringResource(step.titleRes),
                    subtitle = step.subtitleRes?.let { stringResource(it) },
                    action = actionFor(flow.copy(step = step), model, labels, onComplete),
                    contentScrolls = !step.bringsOwnScroll,
                ) {
                    CompleteStepContent(
                        step = step,
                        answers = flow.answers,
                        model = model,
                        domains = domains,
                        collections = collections,
                        error = error,
                        onAchievementCheck = onAchievementCheck,
                    )
                }
            }
        }
    }
}

/**
 * The one action a step offers. Tile steps offer none: the pick is the
 * advance (A-1). Valence commits in place, so it needs Continue; the optional
 * steps read Skip while empty and Done once answered.
 */
private fun actionFor(
    state: CompleteFlowState,
    model: CompleteFlowModel,
    labels: CompleteLabels,
    onComplete: () -> Unit,
): RecordStepAction? =
    when (state.step) {
        CompleteStep.VALENCE ->
            RecordStepAction.Primary(
                label = labels.continueLabel,
                enabled = state.hasAnswer(CompleteStep.VALENCE),
                isLoading = false,
                onClick = model::advance,
            )

        CompleteStep.EMOTION, CompleteStep.DOMAIN, CompleteStep.SUCCESS -> null

        CompleteStep.SOULS, CompleteStep.COLLECTION ->
            RecordStepAction.Text(
                label = if (state.optionalButtonIsSkip) labels.skip else labels.done,
                onClick = model::advance,
            )

        CompleteStep.PRIVACY ->
            RecordStepAction.Primary(
                label = labels.makePebble,
                enabled = !state.isCompleting,
                isLoading = state.isCompleting,
                onClick = onComplete,
            )
    }

/** Resolved strings, so the action builder stays a pure function. */
private data class CompleteLabels(
    val continueLabel: String,
    val skip: String,
    val done: String,
    val makePebble: String,
)
