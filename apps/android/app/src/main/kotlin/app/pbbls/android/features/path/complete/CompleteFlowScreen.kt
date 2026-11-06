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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.rememberTapHaptics
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.flow.RecordFlowChrome
import app.pbbls.android.features.path.flow.RecordStepAction
import app.pbbls.android.features.path.flow.RecordStepScaffold
import app.pbbls.android.features.path.flow.steps.RecordRicherStep
import app.pbbls.android.navigation.CompleteOrigin
import java.time.ZoneId

/** As the record flow: enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * The Complete flow (#1048): a moment becomes a pebble in six steps, then a
 * end screen. A full-screen modal entry (A-14). ✕ never asks (A-4); the
 * answers stay in the per-moment autosave. [onDismiss] is every exit: ✕, back
 * on the first step, and the end screen's Done. [onAddWords] opens Edit moment and
 * [onRecordAnother] a fresh capture (null for a pebble made from the card).
 */
@Composable
fun CompleteFlowScreen(
    momentId: String,
    origin: CompleteOrigin,
    onDismiss: () -> Unit,
    onOpenSupport: () -> Unit,
    onAddWords: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordAnother: (() -> Unit)? = null,
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

    // Words may have been added in Edit moment: the end screen re-reads when it comes back.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    // Always enabled, as in the record flow: back walks the steps, closes on
    // the first one, and leaves from success; it never falls through.
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { viewModel.onSystemBack() }

    CompleteFlowContent(
        uiState = uiState,
        origin = origin,
        model = viewModel.machine(),
        domains = refs.domains,
        souls = refs.souls,
        isSoulsLoaded = refs.hasLoaded,
        palette =
            uiState.flow.answers.emotionId
                ?.let(palettes::palette),
        onClose = viewModel::onClose,
        onComplete = viewModel::complete,
        onExit = viewModel::onExit,
        onAchievementCheck = viewModel::onAchievementCheck,
        hapticsOn = viewModel.hapticsOn,
        onOpenSupport = onOpenSupport,
        onFirstPebbleHelp = viewModel::showFirstPebbleHelp,
        onOpenValenceHelp = viewModel::openValenceHelp,
        onCloseValenceHelp = viewModel::closeValenceHelp,
        collections = refs.collections,
        onEditCollection = viewModel::editCollection,
        onEditVisibility = viewModel::editVisibility,
        onAddWords = onAddWords,
        onRecordAnother = onRecordAnother,
        modifier = modifier,
    )
}

/** Stateless, so the previews render every step without a ViewModel. */
@Composable
fun CompleteFlowContent(
    uiState: CompleteFlowUiState,
    origin: CompleteOrigin,
    model: CompleteFlowModel,
    domains: List<Domain>,
    souls: List<SoulWithGlyph>,
    isSoulsLoaded: Boolean,
    palette: EmotionPalette?,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onExit: () -> Unit,
    hapticsOn: Boolean,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onFirstPebbleHelp: () -> Unit = {},
    onOpenValenceHelp: () -> Unit = {},
    onCloseValenceHelp: () -> Unit = {},
    collections: List<PebbleCollection> = emptyList(),
    onEditCollection: (String?) -> Unit = {},
    onEditVisibility: (Visibility) -> Unit = {},
    onAddWords: () -> Unit = {},
    onRecordAnother: (() -> Unit)? = null,
) {
    val flow = uiState.flow
    val labels =
        CompleteLabels(
            continueLabel = stringResource(R.string.record_action_continue),
        )
    val error = flow.errorRes?.let { stringResource(it) }
    val editError = uiState.editErrorRes?.let { stringResource(it) }
    val recent = uiState.recentDomainIds.mapNotNull { id -> domains.firstOrNull { it.id == id } }
    val bottomInset = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Top and sides only: the bottom is each step's own, so Life's bar can tint down to the screen's edge.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        if (flow.step != CompleteStep.SUCCESS) {
            RecordFlowChrome(
                position = (flow.step.dotIndex ?: 0) + 1,
                total = CompleteStep.counted.size,
                canGoBack = flow.step.previous != null,
                onBack = model::back,
                onClose = onClose,
                modifier = Modifier.readableWidth(),
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
                Box(Modifier.readableWidth().fillMaxSize().then(bottomInset)) {
                    RecordRicherStep(
                        pebbleId = completed.pebbleId,
                        name = uiState.momentName,
                        nameLoaded = uiState.isMomentLoaded,
                        happenedAt = uiState.happenedAt?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate(),
                        glyph = completed.glyph,
                        karmaDelta = completed.karmaDelta,
                        valence = flow.answers.valence ?: Valence.NEUTRAL_MEDIUM,
                        palette = palette,
                        collections = collections,
                        collectionId = flow.answers.collectionId,
                        visibility = flow.answers.visibility,
                        hasWords = uiState.hasWords,
                        isEditing = uiState.isEditing,
                        editError = editError,
                        quiet = flow.answers.valence?.quietSuccess == true,
                        onEditCollection = onEditCollection,
                        onEditVisibility = onEditVisibility,
                        onAddWords = onAddWords,
                        onDone = onExit,
                        onOpenSupport = onOpenSupport,
                        onRecordAnother = onRecordAnother,
                    )
                }
            } else {
                val body: @Composable () -> Unit = {
                    CompleteStepContent(
                        step = step,
                        answers = flow.answers,
                        isFirstPebble = uiState.isFirstPebble,
                        valenceHelpOpen = uiState.valenceHelpOpen,
                        onFirstPebbleHelp = onFirstPebbleHelp,
                        onOpenValenceHelp = onOpenValenceHelp,
                        onCloseValenceHelp = onCloseValenceHelp,
                        model = model,
                        domains = domains,
                        recent = recent,
                        souls = souls,
                        isSoulsLoaded = isSoulsLoaded,
                        error = error,
                        isCompleting = flow.isCompleting,
                        onComplete = onComplete,
                        onAchievementCheck = onAchievementCheck,
                        hapticsOn = hapticsOn,
                    )
                }
                if (step == CompleteStep.LIFE) {
                    // Draws its own title and its bar down to the edge: no scaffold, no bottom inset here. It
                    // caps its own column, so the bar's tint can run the window's width on a tablet.
                    body()
                } else {
                    Box(Modifier.readableWidth().fillMaxSize().then(bottomInset)) {
                        RecordStepScaffold(
                            title = stringResource(step.titleRes),
                            // Only the valence step, and only when the moment was saved a moment ago (D1).
                            subtitle =
                                if (step == CompleteStep.VALENCE && origin != CompleteOrigin.CARD) {
                                    stringResource(R.string.complete_after_save)
                                } else {
                                    null
                                },
                            action = actionFor(flow.copy(step = step), model, labels),
                            contentScrolls = !step.bringsOwnScroll,
                            content = body,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one action a step offers. Valence commits in place, so it needs Continue; the emotion pick is its
 * own advance (A-1), and Life carries its own button in its bar.
 */
private fun actionFor(
    state: CompleteFlowState,
    model: CompleteFlowModel,
    labels: CompleteLabels,
): RecordStepAction? =
    when (state.step) {
        CompleteStep.VALENCE ->
            RecordStepAction.Primary(
                label = labels.continueLabel,
                enabled = state.hasAnswer(CompleteStep.VALENCE),
                isLoading = false,
                onClick = model::advance,
            )

        CompleteStep.EMOTION, CompleteStep.LIFE, CompleteStep.SUCCESS -> null
    }

/** Resolved strings, so the action builder stays a pure function. */
private data class CompleteLabels(
    val continueLabel: String,
)
